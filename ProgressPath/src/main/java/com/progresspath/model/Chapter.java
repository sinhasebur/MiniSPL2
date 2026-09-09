package com.progresspath.model;

import java.time.LocalDate;

public record Chapter(
        long id,
        long courseId,
        String name,
        double weight,
        double progress,
        LocalDate targetDate
) {
    @Override
    public String toString() {
        return name;
    }
}
