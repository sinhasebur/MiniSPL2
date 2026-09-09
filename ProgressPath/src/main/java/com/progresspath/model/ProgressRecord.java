package com.progresspath.model;

import java.time.LocalDateTime;

public record ProgressRecord(
        long id,
        long chapterId,
        double previousProgress,
        double newProgress,
        LocalDateTime recordedAt
) {
}
