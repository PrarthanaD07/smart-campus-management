package edu.smartcampus;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

import edu.smartcampus.model.Booking;
import edu.smartcampus.model.CampusActivity;
import edu.smartcampus.model.CampusResource;
import edu.smartcampus.model.Classroom;
import edu.smartcampus.model.Course;
import edu.smartcampus.model.CourseEnrollment;
import edu.smartcampus.model.Equipment;
import edu.smartcampus.model.Faculty;
import edu.smartcampus.model.Identifiable;
import edu.smartcampus.model.Laboratory;
import edu.smartcampus.model.Student;
import edu.smartcampus.repository.CampusRepositories;
import edu.smartcampus.repository.JdbcCrudRepository;
import edu.smartcampus.service.ActivityLogger;
import edu.smartcampus.service.BookingService;
import edu.smartcampus.service.CsvFileService;

public final class ConsoleMain implements AutoCloseable {
    private static final DateTimeFormatter BOOKING_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final Scanner scanner = new Scanner(System.in);
    private final CampusRepositories repositories = new CampusRepositories();
    private final BookingService bookingService = new BookingService(repositories.bookings);
    private final ActivityLogger logger = new ActivityLogger();
    private final CsvFileService csvFileService = new CsvFileService();

    public static void main(String[] args) {
        try (ConsoleMain application = new ConsoleMain()) {
            application.run();
        }
    }

    private void run() {
        System.out.println("=== Smart Campus Integrated Management System ===");
        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = readLine("Choose an option: ");
            try {
                switch (choice) {
                    case "1" -> manageStudents();
                    case "2" -> manageFaculty();
                    case "3" -> manageCourses();
                    case "4" -> manageResources();
                    case "5" -> manageActivities();
                    case "6" -> manageBookings();
                    case "7" -> exportStudents();
                    case "8" -> manageCourseEnrollments();
                    case "0" -> running = false;
                    default -> System.out.println("Choose one of the listed options.");
                }
            } catch (IllegalArgumentException exception) {
                System.out.println("Could not complete the operation: " + exception.getMessage());
            }
        }
        System.out.println("Goodbye.");
    }

    private void printMainMenu() {
        System.out.println("""

                1. Student management
                2. Faculty management
                3. Course management
                4. Resource management
                5. Campus activity management
                6. Resource booking
                7. Export students to CSV
                8. Course enrollments
                0. Exit""");
    }

    private void manageStudents() {
        manageRecords("Students", repositories.students,
                () -> new Student(readLine("Student ID: "), readLine("Name: "), readLine("Email: "),
                        readLine("Program: "), readInt("Year (1-8): ")),
                id -> new Student(id, readLine("Name: "), readLine("Email: "), readLine("Program: "),
                        readInt("Year (1-8): ")));
    }

    private void manageFaculty() {
        manageRecords("Faculty", repositories.faculty,
                () -> new Faculty(readLine("Faculty ID: "), readLine("Name: "), readLine("Email: "),
                        readLine("Department: "), readLine("Designation: ")),
                id -> new Faculty(id, readLine("Name: "), readLine("Email: "), readLine("Department: "),
                        readLine("Designation: ")));
    }

    private void manageCourses() {
        manageRecords("Courses", repositories.courses,
                () -> promptCourse(readLine("Course ID: ")),
                this::promptCourse);
    }

    private Course promptCourse(String id) {
        String name = readLine("Course name: ");
        String facultyId = readLine("Faculty ID (leave blank if unassigned): ");
        if (!facultyId.isBlank() && repositories.faculty.findById(facultyId).isEmpty()) {
            throw new IllegalArgumentException("No faculty member has ID " + facultyId + ".");
        }
        return new Course(id, name, facultyId, readInt("Credits (1-10): "));
    }

    private void manageResources() {
        manageRecords("Resources", repositories.resources,
                () -> promptResource(readLine("Resource ID: ")),
                this::promptResource);
    }

    private CampusResource promptResource(String id) {
        System.out.println("Resource type: 1. Classroom  2. Laboratory  3. Equipment");
        String type = readLine("Choose type: ");
        String name = readLine("Resource name: ");
        int capacity = readInt("Capacity: ");
        return switch (type) {
            case "1" -> new Classroom(id, name, capacity);
            case "2" -> new Laboratory(id, name, capacity);
            case "3" -> new Equipment(id, name, capacity);
            default -> throw new IllegalArgumentException("Choose a valid resource type.");
        };
    }

    private void manageActivities() {
        manageRecords("Campus activities", repositories.activities,
                () -> promptActivity(readLine("Activity ID: ")),
                this::promptActivity);
    }

    private CampusActivity promptActivity(String id) {
        String name = readLine("Activity name: ");
        String date = readLine("Date (yyyy-MM-dd): ");
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Enter the date as yyyy-MM-dd.");
        }
        return new CampusActivity(id, name, date, readLine("Location: "));
    }

    private void manageBookings() {
        System.out.println("""
                1. Create booking
                2. List bookings
                3. Search bookings
                4. Cancel booking
                0. Back""");
        switch (readLine("Choose an option: ")) {
            case "1" -> createBooking();
            case "2" -> printItems(repositories.bookings.findAll());
            case "3" -> searchBookings();
            case "4" -> deleteBooking();
            case "0" -> { }
            default -> System.out.println("Choose one of the listed options.");
        }
    }

    private void createBooking() {
        String id = readLine("Booking ID: ");
        String resourceId = readLine("Resource ID: ");
        if (repositories.resources.findById(resourceId).isEmpty()) {
            throw new IllegalArgumentException("No resource has ID " + resourceId + ".");
        }
        String bookedBy = readLine("Student or faculty ID: ");
        if (repositories.students.findById(bookedBy).isEmpty()
                && repositories.faculty.findById(bookedBy).isEmpty()) {
            throw new IllegalArgumentException("No student or faculty member has ID " + bookedBy + ".");
        }
        LocalDateTime start = readDateTime("Start (yyyy-MM-dd HH:mm): ");
        LocalDateTime end = readDateTime("End (yyyy-MM-dd HH:mm): ");
        Booking booking = new Booking(id, resourceId, bookedBy, start, end);
        try {
            Booking saved = bookingService.bookAsync(booking).get();
            logger.log("Created booking " + saved.getId() + " for resource " + saved.resourceId());
            System.out.println("Booking saved: " + saved);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Booking was interrupted.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause == null) {
                throw new IllegalStateException("Could not save booking.", exception);
            }
            if (cause instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("Could not save booking: " + cause.getMessage(), cause);
        }
    }

    private void searchBookings() {
        String query = readLine("Search by booking ID, resource ID, or booker ID: ").toLowerCase();
        List<Booking> results = repositories.bookings.findAll().stream()
                .filter(booking -> booking.toString().toLowerCase().contains(query))
                .toList();
        printItems(results);
    }

    private void deleteBooking() {
        String id = readLine("Booking ID to cancel: ");
        if (repositories.bookings.delete(id)) {
            logger.log("Cancelled booking " + id);
            System.out.println("Booking cancelled.");
        } else {
            System.out.println("No booking found with ID " + id + ".");
        }
    }

    private void manageCourseEnrollments() {
        System.out.println("""
                1. Enroll student in course
                2. View all enrollments
                3. Search enrollments
                4. Remove student from course
                0. Back""");
        switch (readLine("Choose an option: ")) {
            case "1" -> enrollStudent();
            case "2" -> printItems(repositories.enrollments.findAll());
            case "3" -> {
                String query = readLine("Search by course or student ID: ").toLowerCase();
                printItems(repositories.enrollments.findAll().stream()
                        .filter(enrollment -> enrollment.toString().toLowerCase().contains(query))
                        .toList());
            }
            case "4" -> removeEnrollment();
            case "0" -> { }
            default -> System.out.println("Choose one of the listed options.");
        }
    }

    private void enrollStudent() {
        String courseId = readLine("Course ID: ");
        if (repositories.courses.findById(courseId).isEmpty()) {
            throw new IllegalArgumentException("No course has ID " + courseId + ".");
        }
        String studentId = readLine("Student ID: ");
        if (repositories.students.findById(studentId).isEmpty()) {
            throw new IllegalArgumentException("No student has ID " + studentId + ".");
        }
        String enrollmentId = enrollmentId(courseId, studentId);
        if (repositories.enrollments.findById(enrollmentId).isPresent()) {
            throw new IllegalArgumentException("That student is already enrolled in this course.");
        }
        repositories.enrollments.save(new CourseEnrollment(enrollmentId, courseId, studentId));
        logger.log("Enrolled student " + studentId + " in course " + courseId);
        System.out.println("Student enrolled.");
    }

    private void removeEnrollment() {
        String courseId = readLine("Course ID: ");
        String studentId = readLine("Student ID: ");
        if (repositories.enrollments.delete(enrollmentId(courseId, studentId))) {
            logger.log("Removed student " + studentId + " from course " + courseId);
            System.out.println("Enrollment removed.");
        } else {
            System.out.println("That student is not enrolled in the course.");
        }
    }

    private static String enrollmentId(String courseId, String studentId) {
        return courseId.trim() + "::" + studentId.trim();
    }

    private void exportStudents() {
        Path output = csvFileService.exportStudents(repositories.students.findAll(),
                Path.of("data", "students.csv"));
        logger.log("Exported student records to " + output);
        System.out.println("Student records exported to " + output.toAbsolutePath());
    }

    private <T extends edu.smartcampus.model.Identifiable> void manageRecords(
            String label,
            JdbcCrudRepository<T> repository,
            Supplier<T> createPrompt,
            Function<String, T> updatePrompt) {
        boolean managing = true;
        while (managing) {
            System.out.println("\n" + label + " | 1. Add  2. View all  3. Search  4. Update  5. Delete  0. Back");
            switch (readLine("Choose an option: ")) {
                case "1" -> {
                    T item = createPrompt.get();
                    repository.save(item);
                    logger.log("Created " + label + " record " + item.getId());
                    System.out.println("Record saved.");
                }
                case "2" -> printItems(repository.findAll());
                case "3" -> searchRecords(repository);
                case "4" -> {
                    String id = readLine("ID to update: ");
                    Optional<T> existing = repository.findById(id);
                    if (existing.isEmpty()) {
                        System.out.println("No record found with ID " + id + ".");
                    } else {
                        T item = updatePrompt.apply(id);
                        repository.save(item);
                        logger.log("Updated " + label + " record " + id);
                        System.out.println("Record updated.");
                    }
                }
                case "5" -> {
                    String id = readLine("ID to delete: ");
                    if (repository.delete(id)) {
                        logger.log("Deleted " + label + " record " + id);
                        System.out.println("Record deleted.");
                    } else {
                        System.out.println("No record found with ID " + id + ".");
                    }
                }
                case "0" -> managing = false;
                default -> System.out.println("Choose one of the listed options.");
            }
        }
    }

    private <T extends Identifiable> void searchRecords(JdbcCrudRepository<T> repository) {
        String query = readLine("Search by ID or text: ").toLowerCase();
        List<T> results = repository.findAll().stream()
                .filter(item -> item.toString().toLowerCase().contains(query))
                .toList();
        printItems(results);
    }

    private static void printItems(List<?> items) {
        if (items.isEmpty()) {
            System.out.println("No records found.");
            return;
        }
        items.forEach(System.out::println);
    }

    private LocalDateTime readDateTime(String prompt) {
        try {
            return LocalDateTime.parse(readLine(prompt), BOOKING_TIME);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Enter the date and time as yyyy-MM-dd HH:mm.");
        }
    }

    private int readInt(String prompt) {
        try {
            return Integer.parseInt(readLine(prompt));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole number.");
        }
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    @Override
    public void close() {
        bookingService.close();
        repositories.close();
        scanner.close();
    }
}
