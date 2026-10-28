package edu.smartcampus.model;

import java.time.LocalDateTime;

public record Booking(String id, String resourceId, String bookedBy,
                      LocalDateTime startTime, LocalDateTime endTime) implements Identifiable {
    public Booking {
        if (id == null || id.isBlank() || resourceId == null || resourceId.isBlank()
                || bookedBy == null || bookedBy.isBlank()) {
            throw new IllegalArgumentException("Booking ID, resource ID, and booker cannot be empty.");
        }
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Booking end time must be after its start time.");
        }
        id = id.trim();
        resourceId = resourceId.trim();
        bookedBy = bookedBy.trim();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "%s | Resource: %s | By: %s | %s to %s".formatted(
                id, resourceId, bookedBy, startTime, endTime);
    }
}
