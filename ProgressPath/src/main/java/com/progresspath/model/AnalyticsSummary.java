package com.progresspath.model;

public record AnalyticsSummary(
        double actualProgress,
        double expectedProgress,
        long focusSeconds,
        int completedAssignments,
        int overdueAssignments
) {
}
