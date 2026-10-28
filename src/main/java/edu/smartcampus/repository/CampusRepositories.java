package edu.smartcampus.repository;

import edu.smartcampus.model.Booking;
import edu.smartcampus.model.CampusActivity;
import edu.smartcampus.model.CampusResource;
import edu.smartcampus.model.Classroom;
import edu.smartcampus.model.CourseEnrollment;
import edu.smartcampus.model.Course;
import edu.smartcampus.model.Equipment;
import edu.smartcampus.model.Faculty;
import edu.smartcampus.model.Laboratory;
import edu.smartcampus.model.Student;

import java.time.LocalDateTime;
import java.util.List;

public final class CampusRepositories implements AutoCloseable {
    private final JdbcDatabase database;
    public final JdbcCrudRepository<Student> students;
    public final JdbcCrudRepository<Faculty> faculty;
    public final JdbcCrudRepository<Course> courses;
    public final JdbcCrudRepository<CampusResource> resources;
    public final JdbcCrudRepository<CampusActivity> activities;
    public final JdbcCrudRepository<Booking> bookings;
    public final JdbcCrudRepository<CourseEnrollment> enrollments;

    public CampusRepositories() {
        this(new JdbcDatabase());
    }

    public CampusRepositories(String databaseUrl) {
        this(new JdbcDatabase(databaseUrl));
    }

    private CampusRepositories(JdbcDatabase database) {
        this.database = database;
        this.database.initializeSchema();
        students = new JdbcCrudRepository<>(database, "students",
                List.of("name", "email", "program", "year"),
                row -> new Student(read(row, "id"), read(row, "name"), read(row, "email"),
                        read(row, "program"), integer(row, "year")),
                student -> List.of(student.getName(), student.getEmail(), student.getProgram(), student.getYear()));
        faculty = new JdbcCrudRepository<>(database, "faculty",
                List.of("name", "email", "department", "designation"),
                row -> new Faculty(read(row, "id"), read(row, "name"), read(row, "email"),
                        read(row, "department"), read(row, "designation")),
                person -> List.of(person.getName(), person.getEmail(), person.getDepartment(), person.getDesignation()));
        courses = new JdbcCrudRepository<>(database, "courses",
                List.of("name", "faculty_id", "credits"),
                row -> new Course(read(row, "id"), read(row, "name"), read(row, "faculty_id"),
                        integer(row, "credits")),
                course -> List.of(course.name(), course.facultyId(), course.credits()));
        resources = new JdbcCrudRepository<>(database, "resources",
                List.of("type", "name", "capacity"),
                row -> resource(read(row, "type"), read(row, "id"), read(row, "name"), integer(row, "capacity")),
                resource -> List.of(resource.getType(), resource.getName(), resource.getCapacity()));
        activities = new JdbcCrudRepository<>(database, "activities",
                List.of("name", "date", "location"),
                row -> new CampusActivity(read(row, "id"), read(row, "name"), read(row, "date"),
                        read(row, "location")),
                activity -> List.of(activity.name(), activity.date(), activity.location()));
        bookings = new JdbcCrudRepository<>(database, "bookings",
                List.of("resource_id", "booked_by", "start_time", "end_time"),
                row -> new Booking(read(row, "id"), read(row, "resource_id"), read(row, "booked_by"),
                        LocalDateTime.parse(read(row, "start_time")), LocalDateTime.parse(read(row, "end_time"))),
                booking -> List.of(booking.resourceId(), booking.bookedBy(),
                        booking.startTime().toString(), booking.endTime().toString()));
        enrollments = new JdbcCrudRepository<>(database, "course_enrollments",
                List.of("course_id", "student_id"),
                row -> new CourseEnrollment(read(row, "id"), read(row, "course_id"), read(row, "student_id")),
                enrollment -> List.of(enrollment.courseId(), enrollment.studentId()));
    }

    private static CampusResource resource(String type, String id, String name, int capacity) {
        return switch (type) {
            case "Classroom" -> new Classroom(id, name, capacity);
            case "Laboratory" -> new Laboratory(id, name, capacity);
            case "Equipment" -> new Equipment(id, name, capacity);
            default -> throw new IllegalStateException("Unknown resource type in database: " + type);
        };
    }

    private static String read(java.sql.ResultSet row, String column) {
        try {
            return row.getString(column);
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Could not read database column " + column + ".", exception);
        }
    }

    private static int integer(java.sql.ResultSet row, String column) {
        try {
            return row.getInt(column);
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Could not read database column " + column + ".", exception);
        }
    }

    @Override
    public void close() {
        database.close();
    }
}
