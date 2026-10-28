package edu.smartcampus;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.smartcampus.model.Booking;
import edu.smartcampus.model.CampusActivity;
import edu.smartcampus.model.CampusResource;
import edu.smartcampus.model.Classroom;
import edu.smartcampus.model.Course;
import edu.smartcampus.model.CourseEnrollment;
import edu.smartcampus.model.Equipment;
import edu.smartcampus.model.Faculty;
import edu.smartcampus.model.Laboratory;
import edu.smartcampus.model.Student;
import edu.smartcampus.repository.CampusRepositories;
import edu.smartcampus.service.ActivityLogger;
import edu.smartcampus.service.BookingService;
import edu.smartcampus.service.CsvFileService;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CampusWebServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);
    private final CampusRepositories repositories;
    private final BookingService bookingService;
    private final GoogleAuthManager auth;
    private final ActivityLogger logger = new ActivityLogger();
    private final CsvFileService csvFileService = new CsvFileService();

    CampusWebServer(int port) throws IOException {
        this(System.getenv().getOrDefault("HOST", "127.0.0.1"), port,
                System.getenv().getOrDefault("CAMPUS_DB_URL", "jdbc:sqlite:data/campus.db"));
    }

    CampusWebServer(String host, int port, String databaseUrl) throws IOException {
        this(host, port, databaseUrl, new GoogleAuthManager());
    }

    CampusWebServer(String host, int port, String databaseUrl, GoogleAuthManager auth) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.repositories = new CampusRepositories(databaseUrl);
        this.bookingService = new BookingService(repositories.bookings);
        this.auth = auth;
        this.server.setExecutor(executor);
        this.server.createContext("/auth", this::handleAuth);
        this.server.createContext("/api", this::handleApi);
        this.server.createContext("/", this::handleStatic);
    }

    void start() {
        server.start();
    }

    void stop() {
        server.stop(1);
    }

    int port() {
        return server.getAddress().getPort();
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Only GET is supported for static files.");
            return;
        }

        String requestPath = exchange.getRequestURI().getPath();
        if (requestPath.equals("/healthz")) {
            byte[] content = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
            return;
        }
        if (requestPath.equals("/login") && currentUser(exchange) != null) {
            redirect(exchange, "/");
            return;
        }
        if (requestPath.equals("/") || requestPath.equals("/index.html")) {
            if (currentUser(exchange) == null) {
                redirect(exchange, "/login");
                return;
            }
        }
        String resourcePath = switch (requestPath) {
            case "/" -> "/static/index.html";
            case "/login" -> "/static/login.html";
            case "/assets/styles.css" -> "/static/styles.css";
            case "/assets/app.js" -> "/static/app.js";
            case "/assets/login.css" -> "/static/login.css";
            case "/assets/login.js" -> "/static/login.js";
            default -> null;
        };

        if (resourcePath == null) {
            sendError(exchange, 404, "Page or asset not found.");
            return;
        }

        try (InputStream input = CampusWebServer.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                sendError(exchange, 404, "Page or asset not found.");
                return;
            }

            byte[] content = input.readAllBytes();
            String contentType = requestPath.endsWith(".css")
                    ? "text/css; charset=utf-8"
                    : requestPath.endsWith(".js") ? "text/javascript; charset=utf-8" : "text/html; charset=utf-8";
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
        } finally {
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        try {
            if (currentUser(exchange) == null) {
                sendError(exchange, 401, "Please sign in with Google to continue.");
                return;
            }
            routeApi(exchange);
        } catch (java.time.DateTimeException exception) {
            sendError(exchange, 400, "The date or time value is invalid.");
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
            sendError(exchange, 500, "The server could not process the request.");
        } finally {
            exchange.close();
        }
    }

    private void handleAuth(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                switch (path) {
                    case "/auth/config" -> {
                        if (!method.equals("GET")) {
                            sendError(exchange, 405, "Only GET is supported.");
                        } else {
                            sendJson(exchange, 200, Map.of("configured", auth.isConfigured()));
                        }
                    }
                    case "/auth/google" -> {
                        if (!method.equals("GET")) {
                            sendError(exchange, 405, "Only GET is supported.");
                        } else if (currentUser(exchange) != null) {
                            redirect(exchange, "/");
                        } else {
                            try {
                                redirect(exchange, auth.beginLogin());
                            } catch (IllegalStateException exception) {
                                redirect(exchange, "/login?error=not-configured");
                            }
                        }
                    }
                    case "/auth/google/callback" -> handleGoogleCallback(exchange);
                    case "/auth/me" -> {
                        if (!method.equals("GET")) {
                            sendError(exchange, 405, "Only GET is supported.");
                        } else {
                            GoogleAuthManager.User user = currentUser(exchange);
                            if (user == null) {
                                sendError(exchange, 401, "Please sign in to continue.");
                            } else {
                                sendJson(exchange, 200, Map.of(
                                        "id", user.id(), "name", user.name(),
                                        "email", user.email(), "picture", user.picture()));
                            }
                        }
                    }
                    case "/auth/logout" -> {
                        if (!method.equals("POST")) {
                            sendError(exchange, 405, "Only POST is supported.");
                        } else {
                            auth.endSession(sessionToken(exchange));
                            clearSessionCookie(exchange);
                            sendJson(exchange, 200, Map.of("signedOut", true));
                        }
                    }
                    default -> sendError(exchange, 404, "Authentication route not found.");
                }
            } catch (IllegalArgumentException exception) {
                redirect(exchange, "/login?error=sign-in-failed");
            } catch (Exception exception) {
                System.err.println("Google sign-in failed: " + exception.getClass().getSimpleName());
                redirect(exchange, "/login?error=google-unavailable");
            } finally {
                exchange.close();
            }
        }

    private void handleGoogleCallback(HttpExchange exchange) throws Exception {
            if (!exchange.getRequestMethod().equals("GET")) {
                sendError(exchange, 405, "Only GET is supported.");
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            if (query.containsKey("error")) {
                redirect(exchange, "/login?error=cancelled");
                return;
            }
            GoogleAuthManager.User user = auth.completeLogin(query.get("state"), query.get("code"));
            String session = auth.createSession(user);
            setSessionCookie(exchange, session);
            redirect(exchange, "/");
        }

    private GoogleAuthManager.User currentUser(HttpExchange exchange) {
            return auth.findUser(sessionToken(exchange));
        }

    private String sessionToken(HttpExchange exchange) {
            String cookieName = auth.cookieName() + "=";
            for (String cookieHeader : exchange.getRequestHeaders().getOrDefault("Cookie", List.of())) {
                for (String cookie : cookieHeader.split(";")) {
                    String trimmed = cookie.trim();
                    if (trimmed.startsWith(cookieName)) {
                        return trimmed.substring(cookieName.length());
                    }
                }
            }
            return null;
        }

    private void setSessionCookie(HttpExchange exchange, String session) {
            String secure = auth.redirectUri().startsWith("https://") ? "; Secure" : "";
            exchange.getResponseHeaders().add("Set-Cookie",
                    auth.cookieName() + "=" + session + "; Path=/; Max-Age=43200; HttpOnly; SameSite=Lax" + secure);
        }

    private void clearSessionCookie(HttpExchange exchange) {
            String secure = auth.redirectUri().startsWith("https://") ? "; Secure" : "";
            exchange.getResponseHeaders().add("Set-Cookie",
                    auth.cookieName() + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax" + secure);
        }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
            exchange.getResponseHeaders().set("Location", location);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(302, -1);
        }

    private static Map<String, String> parseQuery(String query) {
            Map<String, String> values = new LinkedHashMap<>();
            if (query == null || query.isBlank()) {
                return values;
            }
            for (String part : query.split("&")) {
                String[] pair = part.split("=", 2);
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                values.put(key, value);
            }
            return values;
        }

        private void routeApi(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String[] parts = exchange.getRequestURI().getPath().split("/", -1);

        if (parts.length >= 3 && "export".equals(parts[2]) && "GET".equals(method)) {
            exportStudents(exchange);
            return;
        }

        if (parts.length < 3 || parts[2].isBlank()) {
            sendError(exchange, 404, "API route not found.");
            return;
        }

        String collection = parts[2];
        if (parts.length == 3 && "GET".equals(method)) {
            sendJson(exchange, 200, collection(collection));
            return;
        }

        if (parts.length == 3 && "POST".equals(method)) {
            Map<String, Object> body = parseJsonObject(readAll(exchange.getRequestBody()));
            sendJson(exchange, 200, save(collection, body));
            return;
        }

        if (parts.length == 4 && "PUT".equals(method)) {
            String id = URLDecoder.decode(parts[3], StandardCharsets.UTF_8);
            Map<String, Object> body = parseJsonObject(readAll(exchange.getRequestBody()));
            sendJson(exchange, 200, update(collection, id, body));
            return;
        }

        if (parts.length == 4 && "DELETE".equals(method)) {
            String id = URLDecoder.decode(parts[3], StandardCharsets.UTF_8);
            boolean deleted = delete(collection, id);
            if (!deleted) {
                sendError(exchange, 404, "No matching record was found.");
                return;
            }
            sendJson(exchange, 200, Map.of("deleted", true));
            return;
        }

        sendError(exchange, 405, "That HTTP method is not supported for this endpoint.");
    }

    private Object collection(String name) {
        return switch (name) {
            case "students" -> repositories.students.findAll();
            case "faculty" -> repositories.faculty.findAll();
            case "courses" -> repositories.courses.findAll();
            case "resources" -> repositories.resources.findAll();
            case "activities" -> repositories.activities.findAll();
            case "bookings" -> repositories.bookings.findAll();
            case "enrollments" -> repositories.enrollments.findAll();
            case "summary" -> Map.of(
                    "students", repositories.students.findAll().size(),
                    "faculty", repositories.faculty.findAll().size(),
                    "courses", repositories.courses.findAll().size(),
                    "resources", repositories.resources.findAll().size(),
                    "activities", repositories.activities.findAll().size(),
                    "bookings", repositories.bookings.findAll().size());
            default -> throw new IllegalArgumentException("Unknown collection: " + name);
        };
    }

    private Object save(String collection, Map<String, Object> body) {
        return switch (collection) {
            case "students" -> {
                Student student = new Student(text(body, "id"), text(body, "name"), text(body, "email"),
                        text(body, "program"), integer(body, "year"));
                repositories.students.save(student);
                logger.log("Saved student " + student.getId());
                yield student;
            }
            case "faculty" -> {
                Faculty faculty = new Faculty(text(body, "id"), text(body, "name"), text(body, "email"),
                        text(body, "department"), text(body, "designation"));
                repositories.faculty.save(faculty);
                logger.log("Saved faculty member " + faculty.getId());
                yield faculty;
            }
            case "courses" -> {
                String facultyId = optionalText(body, "facultyId");
                if (!facultyId.isBlank() && repositories.faculty.findById(facultyId).isEmpty()) {
                    throw new IllegalArgumentException("No faculty member has ID " + facultyId + ".");
                }
                Course course = new Course(text(body, "id"), text(body, "name"), facultyId, integer(body, "credits"));
                repositories.courses.save(course);
                logger.log("Saved course " + course.id());
                yield course;
            }
            case "resources" -> {
                String id = text(body, "id");
                String type = text(body, "type");
                String name = text(body, "name");
                int capacity = integer(body, "capacity");
                CampusResource resource = switch (type) {
                    case "Classroom" -> new Classroom(id, name, capacity);
                    case "Laboratory" -> new Laboratory(id, name, capacity);
                    case "Equipment" -> new Equipment(id, name, capacity);
                    default -> throw new IllegalArgumentException("Select a valid resource type.");
                };
                repositories.resources.save(resource);
                logger.log("Saved resource " + resource.getId());
                yield resource;
            }
            case "activities" -> {
                String date = text(body, "date");
                LocalDate.parse(date);
                CampusActivity activity = new CampusActivity(text(body, "id"), text(body, "name"), date, text(body, "location"));
                repositories.activities.save(activity);
                logger.log("Saved activity " + activity.id());
                yield activity;
            }
            case "bookings" -> {
                String resourceId = text(body, "resourceId");
                if (repositories.resources.findById(resourceId).isEmpty()) {
                    throw new IllegalArgumentException("No resource has ID " + resourceId + ".");
                }
                String bookedBy = text(body, "bookedBy");
                if (repositories.students.findById(bookedBy).isEmpty() && repositories.faculty.findById(bookedBy).isEmpty()) {
                    throw new IllegalArgumentException("No student or faculty member has ID " + bookedBy + ".");
                }
                Booking booking = new Booking(text(body, "id"), resourceId, bookedBy,
                        LocalDateTime.parse(text(body, "startTime").replace(" ", "T")),
                        LocalDateTime.parse(text(body, "endTime").replace(" ", "T")));
                Booking saved = bookingService.book(booking);
                logger.log("Created booking " + saved.getId() + " for resource " + saved.resourceId());
                yield saved;
            }
            case "enrollments" -> {
                String courseId = text(body, "courseId");
                String studentId = text(body, "studentId");
                if (repositories.courses.findById(courseId).isEmpty()) {
                    throw new IllegalArgumentException("No course has ID " + courseId + ".");
                }
                if (repositories.students.findById(studentId).isEmpty()) {
                    throw new IllegalArgumentException("No student has ID " + studentId + ".");
                }
                String id = courseId + "::" + studentId;
                if (repositories.enrollments.findById(id).isPresent()) {
                    throw new IllegalArgumentException("That student is already enrolled in this course.");
                }
                CourseEnrollment enrollment = new CourseEnrollment(id, courseId, studentId);
                repositories.enrollments.save(enrollment);
                logger.log("Enrolled student " + studentId + " in course " + courseId);
                yield enrollment;
            }
            default -> throw new IllegalArgumentException("Unknown collection: " + collection);
        };
    }

    private boolean delete(String collection, String id) {
        return switch (collection) {
            case "students" -> repositories.students.delete(id);
            case "faculty" -> repositories.faculty.delete(id);
            case "courses" -> repositories.courses.delete(id);
            case "resources" -> repositories.resources.delete(id);
            case "activities" -> repositories.activities.delete(id);
            case "bookings" -> repositories.bookings.delete(id);
            case "enrollments" -> repositories.enrollments.delete(id);
            default -> throw new IllegalArgumentException("Unknown collection: " + collection);
        };
    }

    private Object update(String collection, String id, Map<String, Object> body) {
        return switch (collection) {
            case "students" -> {
                requireExisting(repositories.students, id);
                yield save(collection, withId(body, id));
            }
            case "faculty" -> {
                requireExisting(repositories.faculty, id);
                yield save(collection, withId(body, id));
            }
            case "courses" -> {
                requireExisting(repositories.courses, id);
                yield save(collection, withId(body, id));
            }
            case "resources" -> {
                requireExisting(repositories.resources, id);
                yield save(collection, withId(body, id));
            }
            case "activities" -> {
                requireExisting(repositories.activities, id);
                yield save(collection, withId(body, id));
            }
            case "bookings" -> {
                if (repositories.bookings.findById(id).isEmpty()) {
                    throw new IllegalArgumentException("No booking has ID " + id + ".");
                }
                Map<String, Object> values = withId(body, id);
                String resourceId = text(values, "resourceId");
                if (repositories.resources.findById(resourceId).isEmpty()) {
                    throw new IllegalArgumentException("No resource has ID " + resourceId + ".");
                }
                String bookedBy = text(values, "bookedBy");
                if (repositories.students.findById(bookedBy).isEmpty()
                        && repositories.faculty.findById(bookedBy).isEmpty()) {
                    throw new IllegalArgumentException("No student or faculty member has ID " + bookedBy + ".");
                }
                Booking booking = new Booking(id, resourceId, bookedBy,
                        LocalDateTime.parse(text(values, "startTime").replace(" ", "T")),
                        LocalDateTime.parse(text(values, "endTime").replace(" ", "T")));
                Booking saved = bookingService.update(booking);
                logger.log("Updated booking " + id);
                yield saved;
            }
            case "enrollments" -> {
                if (repositories.enrollments.findById(id).isEmpty()) {
                    throw new IllegalArgumentException("No enrollment was found.");
                }
                String courseId = text(body, "courseId");
                String studentId = text(body, "studentId");
                if (repositories.courses.findById(courseId).isEmpty()) {
                    throw new IllegalArgumentException("No course has ID " + courseId + ".");
                }
                if (repositories.students.findById(studentId).isEmpty()) {
                    throw new IllegalArgumentException("No student has ID " + studentId + ".");
                }
                String newId = courseId + "::" + studentId;
                if (!newId.equals(id) && repositories.enrollments.findById(newId).isPresent()) {
                    throw new IllegalArgumentException("That student is already enrolled in this course.");
                }
                repositories.enrollments.delete(id);
                CourseEnrollment enrollment = new CourseEnrollment(newId, courseId, studentId);
                repositories.enrollments.save(enrollment);
                logger.log("Updated course enrollment " + id + " to " + newId);
                yield enrollment;
            }
            default -> throw new IllegalArgumentException("Unknown collection: " + collection);
        };
    }

    private static Map<String, Object> withId(Map<String, Object> body, String id) {
        Map<String, Object> values = new LinkedHashMap<>(body);
        values.put("id", id);
        return values;
    }

    private static <T extends edu.smartcampus.model.Identifiable> void requireExisting(
            edu.smartcampus.repository.JdbcCrudRepository<T> repository, String id) {
        if (repository.findById(id).isEmpty()) {
            throw new IllegalArgumentException("No record has ID " + id + ".");
        }
    }

    private void exportStudents(HttpExchange exchange) throws IOException {
        Path file = csvFileService.exportStudents(repositories.students.findAll(), Path.of("data", "students.csv"));
        byte[] bytes = java.nio.file.Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"students.csv\"");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static String readAll(java.io.InputStream input) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString();
    }

    static Map<String, Object> parseJsonObject(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return Map.of();
        }
        if (text.charAt(0) != '{') {
            throw new IllegalArgumentException("Expected a JSON object body.");
        }

        int[] index = {0};
        Object value = parseValue(text, index);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected a JSON object body.");
        }
        return (Map<String, Object>) map;
    }

    private static Object parseValue(String text, int[] index) {
        skipWhitespace(text, index);
        if (index[0] >= text.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON input.");
        }

        char c = text.charAt(index[0]);
        if (c == '{') {
            index[0]++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace(text, index);
            if (text.charAt(index[0]) == '}') {
                index[0]++;
                return map;
            }
            while (true) {
                String key = parseString(text, index);
                skipWhitespace(text, index);
                expect(':', text, index);
                Object value = parseValue(text, index);
                map.put(key, value);
                skipWhitespace(text, index);
                if (text.charAt(index[0]) == '}') {
                    index[0]++;
                    return map;
                }
                expect(',', text, index);
            }
        }
        if (c == '[') {
            index[0]++;
            List<Object> list = new ArrayList<>();
            skipWhitespace(text, index);
            if (text.charAt(index[0]) == ']') {
                index[0]++;
                return list;
            }
            while (true) {
                list.add(parseValue(text, index));
                skipWhitespace(text, index);
                if (text.charAt(index[0]) == ']') {
                    index[0]++;
                    return list;
                }
                expect(',', text, index);
            }
        }
        if (c == '"') {
            return parseString(text, index);
        }
        if (c == '-' || Character.isDigit(c)) {
            int start = index[0];
            if (c == '-') index[0]++;
            while (index[0] < text.length() && Character.isDigit(text.charAt(index[0]))) {
                index[0]++;
            }
            if (index[0] < text.length() && text.charAt(index[0]) == '.') {
                index[0]++;
                while (index[0] < text.length() && Character.isDigit(text.charAt(index[0]))) {
                    index[0]++;
                }
            }
            return Long.parseLong(text.substring(start, index[0]));
        }
        if (text.startsWith("true", index[0])) {
            index[0] += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", index[0])) {
            index[0] += 5;
            return Boolean.FALSE;
        }
        if (text.startsWith("null", index[0])) {
            index[0] += 4;
            return null;
        }
        throw new IllegalArgumentException("Unsupported JSON value near index " + index[0]);
    }

    private static String parseString(String text, int[] index) {
        expect('"', text, index);
        StringBuilder builder = new StringBuilder();
        while (index[0] < text.length()) {
            char current = text.charAt(index[0]++);
            if (current == '"') {
                return builder.toString();
            }
            if (current == '\\') {
                if (index[0] >= text.length()) {
                    throw new IllegalArgumentException("Invalid JSON escape sequence.");
                }
                char escaped = text.charAt(index[0]++);
                builder.append(switch (escaped) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'u' -> parseUnicodeEscape(text, index);
                    default -> escaped;
                });
            } else {
                builder.append(current);
            }
        }
        throw new IllegalArgumentException("Unterminated JSON string.");
    }

    private static char parseUnicodeEscape(String text, int[] index) {
        if (index[0] + 4 > text.length()) {
            throw new IllegalArgumentException("Invalid Unicode escape sequence.");
        }
        try {
            char decoded = (char) Integer.parseInt(text.substring(index[0], index[0] + 4), 16);
            index[0] += 4;
            return decoded;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid Unicode escape sequence.");
        }
    }

    private static void expect(char expected, String text, int[] index) {
        skipWhitespace(text, index);
        if (index[0] >= text.length() || text.charAt(index[0]) != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "' at index " + index[0]);
        }
        index[0]++;
    }

    private static void skipWhitespace(String text, int[] index) {
        while (index[0] < text.length() && Character.isWhitespace(text.charAt(index[0]))) {
            index[0]++;
        }
    }

    private static String text(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.toString().trim();
    }

    private static String optionalText(Map<String, Object> body, String field) {
        Object value = body.get(field);
        return value == null ? "" : value.toString().trim();
    }

    private static int integer(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null) {
            throw new IllegalArgumentException(field + " must be a whole number.");
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a whole number.");
        }
    }

    private static void sendJson(HttpExchange exchange, int status, Object response) throws IOException {
        String json = toJson(response);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Map.of("error", message));
    }

    private static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return '"' + escapeJson(text) + '"';
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(toJson(String.valueOf(entry.getKey())));
                builder.append(':');
                builder.append(toJson(entry.getValue()));
                first = false;
            }
            builder.append('}');
            return builder.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(toJson(item));
                first = false;
            }
            builder.append(']');
            return builder.toString();
        }
        if (value instanceof Student student) {
            return toJson(Map.of("id", student.getId(), "name", student.getName(),
                    "email", student.getEmail(), "program", student.getProgram(), "year", student.getYear()));
        }
        if (value instanceof Faculty faculty) {
            return toJson(Map.of("id", faculty.getId(), "name", faculty.getName(),
                    "email", faculty.getEmail(), "department", faculty.getDepartment(),
                    "designation", faculty.getDesignation()));
        }
        if (value instanceof CampusResource resource) {
            return toJson(Map.of("id", resource.getId(), "type", resource.getType(),
                    "name", resource.getName(), "capacity", resource.getCapacity()));
        }
        if (value.getClass().isRecord()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            for (java.lang.reflect.RecordComponent component : value.getClass().getRecordComponents()) {
                try {
                    fields.put(component.getName(), component.getAccessor().invoke(value));
                } catch (IllegalAccessException | InvocationTargetException exception) {
                    throw new IllegalStateException("Could not serialize record field " + component.getName(), exception);
                }
            }
            return toJson(fields);
        }
        return toJson(String.valueOf(value));
    }

    private static String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public void close() {
        stop();
        bookingService.close();
        repositories.close();
        executor.shutdown();
    }
}
