package com.progresspath.model;

public enum AssignmentStatus {
    PENDING("Pending"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String displayName;

    AssignmentStatus(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
