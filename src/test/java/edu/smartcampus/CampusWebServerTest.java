package edu.smartcampus;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampusWebServerTest {
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void websiteAndApiRequireAnAuthenticatedGoogleSession() throws Exception {
        try (CampusWebServer server = new CampusWebServer("127.0.0.1", 0, "jdbc:sqlite::memory:")) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();

            assertEquals(200, requestUnauthenticated(base, "GET", "/healthz", null).statusCode());
            HttpResponse<String> homepage = requestUnauthenticated(base, "GET", "/", null);
            assertEquals(302, homepage.statusCode());
            assertEquals("/login", homepage.headers().firstValue("Location").orElseThrow());
            assertEquals(200, requestUnauthenticated(base, "GET", "/login", null).statusCode());
            assertEquals(401, requestUnauthenticated(base, "GET", "/api/students", null).statusCode());
            assertEquals(401, requestUnauthenticated(base, "GET", "/auth/me", null).statusCode());
            assertTrue(requestUnauthenticated(base, "GET", "/auth/config", null)
                    .body().contains("\"configured\":"));
            assertEquals(200, requestUnauthenticated(base, "GET", "/assets/login.css", null).statusCode());
            assertEquals(200, requestUnauthenticated(base, "GET", "/assets/login.js", null).statusCode());
        }
    }

    @Test
    void logoutInvalidatesTheSessionAndClearsItsCookie() throws Exception {
        GoogleAuthManager auth = new GoogleAuthManager();
        String session = auth.createSession(new GoogleAuthManager.User(
                "google-test-id", "Test User", "test@example.edu", ""));
        try (CampusWebServer server = new CampusWebServer(
                "127.0.0.1", 0, "jdbc:sqlite::memory:", auth)) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();
            sessionCookie = auth.cookieName() + "=" + session;

            assertEquals(200, request(base, "GET", "/auth/me", null).statusCode());
            HttpResponse<String> logout = request(base, "POST", "/auth/logout", null);
            assertEquals(200, logout.statusCode());
            assertTrue(logout.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
            assertEquals(401, request(base, "GET", "/auth/me", null).statusCode());
        } finally {
            sessionCookie = null;
        }
    }

    @Test
    void webApiSupportsRecordCrudIncludingBookingsAndEnrollments() throws Exception {
        GoogleAuthManager auth = new GoogleAuthManager();
        String session = auth.createSession(new GoogleAuthManager.User(
                "google-test-id", "Test User", "test@example.edu", ""));
        try (CampusWebServer server = new CampusWebServer(
                "127.0.0.1", 0, "jdbc:sqlite::memory:", auth)) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();
            this.sessionCookie = auth.cookieName() + "=" + session;

            assertEquals(200, request(base, "GET", "/", null).statusCode());
            assertTrue(request(base, "GET", "/auth/me", null).body().contains("test@example.edu"));
            assertEquals(200, request(base, "POST", "/api/students",
                    "{\"id\":\"S1\",\"name\":\"Asha Rao\",\"email\":\"asha@example.edu\",\"program\":\"Computer Science\",\"year\":2}").statusCode());
            assertEquals(200, request(base, "POST", "/api/students",
                    "{\"id\":\"S2\",\"name\":\"Ravi Shah\",\"email\":\"ravi@example.edu\",\"program\":\"Design\",\"year\":1}").statusCode());
            assertEquals(200, request(base, "POST", "/api/faculty",
                    "{\"id\":\"F1\",\"name\":\"Lee Chen\",\"email\":\"lee@example.edu\",\"department\":\"Science\",\"designation\":\"Professor\"}").statusCode());
            assertEquals(200, request(base, "POST", "/api/courses",
                    "{\"id\":\"C1\",\"name\":\"Physics\",\"facultyId\":\"F1\",\"credits\":4}").statusCode());
            assertEquals(200, request(base, "POST", "/api/courses",
                    "{\"id\":\"C2\",\"name\":\"Design\",\"facultyId\":\"\",\"credits\":3}").statusCode());
            assertEquals(200, request(base, "POST", "/api/resources",
                    "{\"id\":\"R1\",\"type\":\"Classroom\",\"name\":\"Room 1\",\"capacity\":30}").statusCode());
            assertEquals(200, request(base, "POST", "/api/activities",
                    "{\"id\":\"A1\",\"name\":\"Campus Fair\",\"date\":\"2026-10-28\",\"location\":\"Quad\"}").statusCode());

            assertEquals(200, request(base, "PUT", "/api/faculty/F1",
                    "{\"name\":\"Lee Chen\",\"email\":\"lee@example.edu\",\"department\":\"Physics\",\"designation\":\"Professor\"}").statusCode());
            assertEquals(200, request(base, "PUT", "/api/courses/C1",
                    "{\"name\":\"Advanced Physics\",\"facultyId\":\"F1\",\"credits\":5}").statusCode());
            assertEquals(200, request(base, "PUT", "/api/resources/R1",
                    "{\"type\":\"Laboratory\",\"name\":\"Physics Lab\",\"capacity\":24}").statusCode());
            assertEquals(200, request(base, "PUT", "/api/activities/A1",
                    "{\"name\":\"Science Fair\",\"date\":\"2026-10-29\",\"location\":\"Main Hall\"}").statusCode());

            HttpResponse<String> students = request(base, "GET", "/api/students", null);
            assertTrue(students.body().contains("\"id\":\"S1\""));
            assertTrue(students.body().contains("\"name\":\"Asha Rao\""));

            assertEquals(200, request(base, "PUT", "/api/students/S1",
                    "{\"name\":\"Asha Nair\",\"email\":\"asha@example.edu\",\"program\":\"Computer Science\",\"year\":3}").statusCode());
            assertTrue(request(base, "GET", "/api/students", null).body().contains("\"name\":\"Asha Nair\""));

            assertEquals(200, request(base, "POST", "/api/bookings",
                    "{\"id\":\"B1\",\"resourceId\":\"R1\",\"bookedBy\":\"S1\",\"startTime\":\"2026-10-28T10:00\",\"endTime\":\"2026-10-28T11:00\"}").statusCode());
            assertEquals(200, request(base, "PUT", "/api/bookings/B1",
                    "{\"resourceId\":\"R1\",\"bookedBy\":\"S1\",\"startTime\":\"2026-10-28T10:30\",\"endTime\":\"2026-10-28T11:30\"}").statusCode());
            assertTrue(request(base, "GET", "/api/bookings", null).body().contains("2026-10-28T10:30"));
            assertEquals(200, request(base, "POST", "/api/bookings",
                    "{\"id\":\"B2\",\"resourceId\":\"R1\",\"bookedBy\":\"S2\",\"startTime\":\"2026-10-28T12:00\",\"endTime\":\"2026-10-28T13:00\"}").statusCode());
            assertEquals(400, request(base, "PUT", "/api/bookings/B1",
                    "{\"resourceId\":\"R1\",\"bookedBy\":\"S1\",\"startTime\":\"2026-10-28T12:00\",\"endTime\":\"2026-10-28T12:30\"}").statusCode());

            assertEquals(200, request(base, "POST", "/api/enrollments",
                    "{\"courseId\":\"C1\",\"studentId\":\"S1\"}").statusCode());
            assertEquals(200, request(base, "PUT", "/api/enrollments/C1%3A%3AS1",
                    "{\"courseId\":\"C2\",\"studentId\":\"S2\"}").statusCode());
            String enrollments = request(base, "GET", "/api/enrollments", null).body();
            assertTrue(enrollments.contains("\"courseId\":\"C2\""));
            assertTrue(enrollments.contains("\"studentId\":\"S2\""));

            assertEquals(200, request(base, "DELETE", "/api/enrollments/C2%3A%3AS2", null).statusCode());
            assertEquals(200, request(base, "DELETE", "/api/bookings/B1", null).statusCode());
            assertEquals(200, request(base, "DELETE", "/api/students/S1", null).statusCode());
            assertEquals(200, request(base, "DELETE", "/api/bookings/B2", null).statusCode());
            assertEquals(200, request(base, "DELETE", "/api/activities/A1", null).statusCode());
            assertEquals(200, request(base, "DELETE", "/api/resources/R1", null).statusCode());
            assertEquals(200, request(base, "DELETE", "/api/courses/C1", null).statusCode());
            assertEquals(200, request(base, "DELETE", "/api/courses/C2", null).statusCode());
            assertEquals(200, request(base, "DELETE", "/api/faculty/F1", null).statusCode());
            assertEquals(200, request(base, "DELETE", "/api/students/S2", null).statusCode());
        } finally {
            sessionCookie = null;
        }
    }

    private String sessionCookie;

    private HttpResponse<String> request(String base, String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));
        if (sessionCookie != null) {
            request.header("Cookie", sessionCookie);
        }
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> requestUnauthenticated(
            String base, String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
