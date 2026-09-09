package com.progresspath.model;

public record Course(long id, long studyPlanId, String code, String name) {
    @Override
    public String toString() {
        return code + " — " + name;
    }
}
