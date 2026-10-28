package edu.smartcampus.model;

public final class Student extends Person {
    private final String program;
    private final int year;

    public Student(String id, String name, String email, String program, int year) {
        super(id, name, email);
        if (program == null || program.isBlank()) {
            throw new IllegalArgumentException("Program cannot be empty.");
        }
        if (year < 1 || year > 8) {
            throw new IllegalArgumentException("Year must be between 1 and 8.");
        }
        this.program = program.trim();
        this.year = year;
    }

    public String getProgram() {
        return program;
    }

    public int getYear() {
        return year;
    }

    @Override
    public String getRole() {
        return "Student";
    }

    @Override
    public String toString() {
        return "%s | %s | %s | %s | Year %d".formatted(getId(), getName(), getEmail(), program, year);
    }
}
