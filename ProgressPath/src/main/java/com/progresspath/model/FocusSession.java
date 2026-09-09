package com.progresspath.model;

import java.time.LocalDateTime;

public record FocusSession(
        long id,
        long courseId,
        Long chapterId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        long durationSeconds,
        FocusSessionStatus status,
        String notes
) {
}
