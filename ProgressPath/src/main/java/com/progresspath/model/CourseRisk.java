package com.progresspath.model;

public record CourseRisk(
        long courseId,
        String courseCode,
        String courseName,
        double actualProgress,
        double expectedProgress
) {
    public double variance() {
        return actualProgress - expectedProgress;
    }
}
