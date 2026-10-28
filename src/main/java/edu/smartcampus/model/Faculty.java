package edu.smartcampus.model;

public final class Faculty extends Person {
    private final String department;
    private final String designation;

    public Faculty(String id, String name, String email, String department, String designation) {
        super(id, name, email);
        if (department == null || department.isBlank() || designation == null || designation.isBlank()) {
            throw new IllegalArgumentException("Department and designation cannot be empty.");
        }
        this.department = department.trim();
        this.designation = designation.trim();
    }

    public String getDepartment() {
        return department;
    }

    public String getDesignation() {
        return designation;
    }

    @Override
    public String getRole() {
        return "Faculty";
    }

    @Override
    public String toString() {
        return "%s | %s | %s | %s | %s".formatted(
                getId(), getName(), getEmail(), department, designation);
    }
}
