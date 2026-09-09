package com.progresspath.model;

import java.time.LocalDate;

public record StudyPlan(long id, String name, LocalDate startDate, LocalDate endDate, boolean archived) {
    @Override
    public String toString() {
        return name;
    }
}
