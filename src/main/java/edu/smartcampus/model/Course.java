package edu.smartcampus.model;

public record Course(String id, String name, String facultyId, int credits) implements Identifiable {
    public Course {
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Course ID and name cannot be empty.");
        }
        if (facultyId == null) {
            facultyId = "";
        }
        if (credits < 1 || credits > 10) {
            throw new IllegalArgumentException("Credits must be between 1 and 10.");
        }
        id = id.trim();
        name = name.trim();
        facultyId = facultyId.trim();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "%s | %s | Faculty: %s | Credits: %d".formatted(
                id, name, facultyId.isBlank() ? "Unassigned" : facultyId, credits);
    }
}
