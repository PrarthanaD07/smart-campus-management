package edu.smartcampus.model;

public abstract class Person implements Identifiable {
    private final String id;
    private final String name;
    private final String email;

    protected Person(String id, String name, String email) {
        this.id = required(id, "ID");
        this.name = required(name, "Name");
        this.email = required(email, "Email");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be empty.");
        }
        return value.trim();
    }

    @Override
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public abstract String getRole();
}
