package edu.smartcampus.model;

public record CampusActivity(String id, String name, String date, String location)
        implements Identifiable {
    public CampusActivity {
        if (id == null || id.isBlank() || name == null || name.isBlank()
                || date == null || date.isBlank() || location == null || location.isBlank()) {
            throw new IllegalArgumentException("Activity fields cannot be empty.");
        }
        id = id.trim();
        name = name.trim();
        date = date.trim();
        location = location.trim();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "%s | %s | %s | %s".formatted(id, name, date, location);
    }
}
