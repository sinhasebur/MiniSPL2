package com.progresspath.persistence;

/**
 * Tiny value object returned by {@link DatabaseSeeder#seedDemoData()} so the
 * CLI seed runner can echo what was inserted (or skipped because data was
 * already present).
 */
public record SeedingReport(
        int plansInserted,
        int coursesInserted,
        int chaptersInserted,
        int progressRecordsInserted,
        int assignmentsInserted,
        int focusSessionsInserted,
        boolean dataAlreadyPresent
) {
    public String summary() {
        return String.format(
                "Seed report -> plans=%d, courses=%d, chapters=%d, progress_records=%d, "
                        + "assignments=%d, focus_sessions=%d (already_seeded=%s)",
                plansInserted, coursesInserted, chaptersInserted, progressRecordsInserted,
                assignmentsInserted, focusSessionsInserted, dataAlreadyPresent);
    }
}
