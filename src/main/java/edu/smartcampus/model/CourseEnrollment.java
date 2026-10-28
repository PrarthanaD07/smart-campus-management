package edu.smartcampus.model;

public record CourseEnrollment(String id, String courseId, String studentId) implements Identifiable {
    public CourseEnrollment {
        if (id == null || id.isBlank() || courseId == null || courseId.isBlank()
                || studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("Course and student IDs cannot be empty.");
        }
        id = id.trim();
        courseId = courseId.trim();
        studentId = studentId.trim();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Course: %s | Student: %s".formatted(courseId, studentId);
    }
}
