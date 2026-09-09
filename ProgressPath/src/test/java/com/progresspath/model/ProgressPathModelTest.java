package com.progresspath.model;

import com.progresspath.persistence.DatabaseManager;
import com.progresspath.progress.WeightedChapterProgressStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressPathModelTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsPlanCourseChapterAndProgress() {
        DatabaseManager database = new DatabaseManager(temporaryDirectory.resolve("progresspath-test.db"));
        ProgressPathModel model = new ProgressPathModel(database);
        StudyPlan plan = model.addStudyPlan(
                "Autumn term", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        Course course = model.addCourse(plan.id(), "cse101", "Programming Fundamentals");
        Chapter first = model.addChapter(
                course.id(), "Foundations", 40, LocalDate.of(2026, 9, 15));
        Chapter second = model.addChapter(
                course.id(), "Applications", 60, LocalDate.of(2026, 10, 15));

        model.updateChapterProgress(first.id(), 50);
        model.updateChapterProgress(second.id(), 100);

        ProgressPathModel reloadedModel = new ProgressPathModel(database);
        PlanSummary summary = reloadedModel.summarize(plan.id(), new WeightedChapterProgressStrategy());
        assertEquals(1, summary.courseCount());
        assertEquals(2, summary.chapterCount());
        assertEquals(80.0, summary.progress(), 0.001);
    }

    @Test
    void rejectsInvalidDatesAndExcessChapterWeight() {
        ProgressPathModel model = new ProgressPathModel(
                new DatabaseManager(temporaryDirectory.resolve("validation-test.db")));

        assertIllegalArgument(new TestAction() {
            @Override public void run() {
                model.addStudyPlan("Invalid", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1));
            }
        });

        StudyPlan plan = model.addStudyPlan(
                "Valid", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        Course course = model.addCourse(plan.id(), "CSE101", "Programming Fundamentals");
        model.addChapter(course.id(), "First", 70, LocalDate.of(2026, 9, 15));

        assertIllegalArgument(new TestAction() {
            @Override public void run() {
                model.addChapter(course.id(), "Second", 40, LocalDate.of(2026, 10, 15));
            }
        });
        assertIllegalArgument(new TestAction() {
            @Override public void run() {
                model.addChapter(course.id(), "Outside term", 10, LocalDate.of(2027, 1, 1));
            }
        });
    }

    private void assertIllegalArgument(TestAction action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    private interface TestAction {
        void run();
    }
}
