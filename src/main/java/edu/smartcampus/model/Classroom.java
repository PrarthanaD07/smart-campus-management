package edu.smartcampus.model;

public final class Classroom extends CampusResource {
    public Classroom(String id, String name, int capacity) {
        super(id, name, capacity);
    }

    @Override
    public String getType() {
        return "Classroom";
    }
}
