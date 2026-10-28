package edu.smartcampus.model;

public final class Equipment extends CampusResource {
    public Equipment(String id, String name, int capacity) {
        super(id, name, capacity);
    }

    @Override
    public String getType() {
        return "Equipment";
    }
}
