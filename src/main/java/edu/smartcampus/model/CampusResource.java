 package edu.smartcampus.model;

public abstract class CampusResource implements Identifiable {
    private final String id;
    private final String name;
    private final int capacity;

    protected CampusResource(String id, String name, int capacity) {
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Resource ID and name cannot be empty.");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be at least 1.");
        }
        this.id = id.trim();
        this.name = name.trim();
        this.capacity = capacity;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public abstract String getType();

    @Override
    public String toString() {
        return "%s | %s | %s | Capacity: %d".formatted(id, getType(), name, capacity);
    }
}
