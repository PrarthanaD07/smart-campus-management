package edu.smartcampus.model;

public final class Laboratory extends CampusResource {
    public Laboratory(String id, String name, int capacity) {
        super(id, name, capacity);
    }

    @Override
    public String getType() {
        return "Laboratory";
    }
}
