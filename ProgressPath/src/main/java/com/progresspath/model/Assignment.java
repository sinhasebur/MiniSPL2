package com.progresspath.model;

import java.time.LocalDate;

public record Assignment(
        long id,
        long courseId,
        String title,
        String description,
        LocalDate dueDate,
        AssignmentPriority priority,
        AssignmentStatus status
) {
}
