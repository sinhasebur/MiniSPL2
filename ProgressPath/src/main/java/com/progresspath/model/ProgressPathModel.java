package com.progresspath.model;

import com.progresspath.persistence.AssignmentRepository;
import com.progresspath.persistence.ChapterRepository;
import com.progresspath.persistence.CourseRepository;
import com.progresspath.persistence.DatabaseManager;
import com.progresspath.persistence.DatabaseSeeder;
import com.progresspath.persistence.FocusSessionRepository;
import com.progresspath.persistence.ProgressRecordRepository;
import com.progresspath.persistence.StudyPlanRepository;
import com.progresspath.progress.ProgressCalculationStrategy;
import com.progresspath.progress.ProgressCalculator;
import com.progresspath.progress.WeightedChapterProgressStrategy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * MVC Model facade. Controllers use this class instead of talking to SQLite directly.
 * It owns validation, calculations, and application-level operations.
 */
public final class ProgressPathModel {
    private final StudyPlanRepository studyPlans;
    private final CourseRepository courses;
    private final ChapterRepository chapters;
    private final ProgressRecordRepository progressRecords;
    private final AssignmentRepository assignments;
    private final FocusSessionRepository focusSessions;
    private final List<ModelChangeListener> listeners = new ArrayList<>();

    public ProgressPathModel() {
        this(new DatabaseManager());
    }

    public ProgressPathModel(DatabaseManager databaseManager) {
        new DatabaseSeeder(databaseManager).initialize();
        studyPlans = new StudyPlanRepository(databaseManager);
        courses = new CourseRepository(databaseManager);
        chapters = new ChapterRepository(databaseManager);
        progressRecords = new ProgressRecordRepository(databaseManager);
        assignments = new AssignmentRepository(databaseManager);
        focusSessions = new FocusSessionRepository(databaseManager);
    }

    public void addChangeListener(ModelChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeChangeListener(ModelChangeListener listener) {
        listeners.remove(listener);
    }

    public List<StudyPlan> getStudyPlans() {
        return studyPlans.findAll();
    }

    public List<StudyPlan> getActiveStudyPlans() {
        List<StudyPlan> activePlans = new ArrayList<>();
        for (StudyPlan plan : getStudyPlans()) {
            if (!plan.archived()) {
                activePlans.add(plan);
            }
        }
        return activePlans;
    }

    public StudyPlan getStudyPlan(long id) {
        Optional<StudyPlan> result = studyPlans.findById(id);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("The study plan no longer exists.");
        }
        return result.get();
    }

    public StudyPlan addStudyPlan(String name, LocalDate startDate, LocalDate endDate) {
        validatePlanDates(startDate, endDate);
        StudyPlan plan = studyPlans.create(requireText(name, "Plan name"), startDate, endDate);
        notifyChange(ModelChangeType.PLAN);
        return plan;
    }

    public void updateStudyPlan(long id, String name, LocalDate startDate, LocalDate endDate) {
        validatePlanDates(startDate, endDate);
        studyPlans.update(id, requireText(name, "Plan name"), startDate, endDate);
        notifyChange(ModelChangeType.PLAN);
    }

    public void setPlanArchived(long id, boolean archived) {
        studyPlans.setArchived(id, archived);
        notifyChange(ModelChangeType.PLAN);
    }

    public void deleteStudyPlan(long id) {
        studyPlans.delete(id);
        notifyChange(ModelChangeType.PLAN);
    }

    public List<Course> getCourses(long studyPlanId) {
        return courses.findByStudyPlan(studyPlanId);
    }

    public Course getCourse(long id) {
        Optional<Course> result = courses.findById(id);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("The course no longer exists.");
        }
        return result.get();
    }

    public Course addCourse(long studyPlanId, String code, String name) {
        getStudyPlan(studyPlanId);
        Course course = courses.create(studyPlanId, cleanCode(code), requireText(name, "Course name"));
        notifyChange(ModelChangeType.COURSE);
        return course;
    }

    public void updateCourse(long id, String code, String name) {
        courses.update(id, cleanCode(code), requireText(name, "Course name"));
        notifyChange(ModelChangeType.COURSE);
    }

    public void deleteCourse(long id) {
        courses.delete(id);
        notifyChange(ModelChangeType.COURSE);
    }

    public List<Chapter> getChapters(long courseId) {
        return chapters.findByCourse(courseId);
    }

    public Chapter getChapter(long id) {
        Optional<Chapter> result = chapters.findById(id);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("The chapter no longer exists.");
        }
        return result.get();
    }

    public Chapter addChapter(long courseId, String name, double weight, LocalDate targetDate) {
        Course course = getCourse(courseId);
        StudyPlan plan = getStudyPlan(course.studyPlanId());
        validateChapter(courseId, null, weight, targetDate, plan);
        Chapter chapter = chapters.create(courseId, requireText(name, "Chapter name"), weight, targetDate);
        notifyChange(ModelChangeType.CHAPTER);
        return chapter;
    }

    public void updateChapter(long id, String name, double weight, LocalDate targetDate) {
        Chapter chapter = getChapter(id);
        Course course = getCourse(chapter.courseId());
        StudyPlan plan = getStudyPlan(course.studyPlanId());
        validateChapter(course.id(), chapter.id(), weight, targetDate, plan);
        chapters.update(id, requireText(name, "Chapter name"), weight, targetDate);
        notifyChange(ModelChangeType.CHAPTER);
    }

    public void deleteChapter(long id) {
        chapters.delete(id);
        notifyChange(ModelChangeType.CHAPTER);
    }

    public void updateChapterProgress(long id, double progress) {
        validatePercentage(progress, "Progress");
        Chapter chapter = getChapter(id);
        if (Double.compare(chapter.progress(), progress) == 0) {
            return;
        }
        progressRecords.create(id, chapter.progress(), progress, LocalDateTime.now());
        chapters.updateProgress(id, progress);
        notifyChange(ModelChangeType.PROGRESS);
    }

    public List<ProgressRecord> getProgressHistory(long chapterId) {
        return progressRecords.findByChapter(chapterId);
    }

    public PlanSummary summarize(long studyPlanId, ProgressCalculationStrategy strategy) {
        List<Course> planCourses = getCourses(studyPlanId);
        List<Chapter> planChapters = chapters.findByStudyPlan(studyPlanId);
        ProgressCalculator calculator = new ProgressCalculator(strategy);
        return new PlanSummary(planCourses.size(), planChapters.size(), calculator.calculate(planChapters));
    }

    public double getExpectedProgress(long studyPlanId, LocalDate date) {
        StudyPlan plan = getStudyPlan(studyPlanId);
        List<Chapter> planChapters = chapters.findByStudyPlan(studyPlanId);
        double totalWeight = 0.0;
        double expectedWeight = 0.0;
        for (Chapter chapter : planChapters) {
            double weight = chapter.weight();
            totalWeight += weight;
            LocalDate target = chapter.targetDate() == null ? plan.endDate() : chapter.targetDate();
            double chapterExpected = expectedChapterProgress(plan.startDate(), target, date);
            expectedWeight += chapterExpected * weight;
        }
        if (totalWeight == 0.0) {
            return 0.0;
        }
        return expectedWeight / totalWeight;
    }

    public String getProgressStatus(double actual, double expected) {
        double variance = actual - expected;
        if (variance >= 5.0) {
            return "Ahead";
        }
        if (variance >= -5.0) {
            return "On track";
        }
        if (variance >= -15.0) {
            return "Slightly behind";
        }
        return "Significantly behind";
    }

    private double expectedChapterProgress(LocalDate start, LocalDate target, LocalDate date) {
        if (!date.isAfter(start)) {
            return 0.0;
        }
        if (!date.isBefore(target)) {
            return 100.0;
        }
        long totalDays = ChronoUnit.DAYS.between(start, target);
        long elapsedDays = ChronoUnit.DAYS.between(start, date);
        if (totalDays <= 0) {
            return 100.0;
        }
        return (elapsedDays * 100.0) / totalDays;
    }

    public List<Assignment> getAssignments(long studyPlanId) {
        return assignments.findByStudyPlan(studyPlanId);
    }

    public Assignment addAssignment(long courseId, String title, String description, LocalDate dueDate,
                                    AssignmentPriority priority, AssignmentStatus status) {
        getCourse(courseId);
        if (dueDate == null) {
            throw new IllegalArgumentException("Assignment due date is required.");
        }
        Assignment assignment = assignments.create(
                courseId,
                requireText(title, "Assignment title"),
                description == null ? "" : description.trim(),
                dueDate,
                priority == null ? AssignmentPriority.MEDIUM : priority,
                status == null ? AssignmentStatus.PENDING : status
        );
        notifyChange(ModelChangeType.ASSIGNMENT);
        return assignment;
    }

    public void updateAssignment(Assignment assignment) {
        if (assignment == null || assignment.dueDate() == null) {
            throw new IllegalArgumentException("Assignment and due date are required.");
        }
        getCourse(assignment.courseId());
        assignments.update(assignment);
        notifyChange(ModelChangeType.ASSIGNMENT);
    }

    public void deleteAssignment(long id) {
        assignments.delete(id);
        notifyChange(ModelChangeType.ASSIGNMENT);
    }

    public List<Assignment> getOverdueAssignments(long studyPlanId, LocalDate today) {
        List<Assignment> overdue = new ArrayList<>();
        for (Assignment assignment : getAssignments(studyPlanId)) {
            if (assignment.dueDate().isBefore(today) && assignment.status() != AssignmentStatus.COMPLETED
                    && assignment.status() != AssignmentStatus.CANCELLED) {
                overdue.add(assignment);
            }
        }
        return overdue;
    }

    public FocusSession saveFocusSession(long courseId, Long chapterId, LocalDateTime startedAt,
                                         LocalDateTime endedAt, long durationSeconds,
                                         FocusSessionStatus status, String notes) {
        getCourse(courseId);
        if (startedAt == null || endedAt == null || durationSeconds < 0 || status == null) {
            throw new IllegalArgumentException("A completed focus session needs valid times and duration.");
        }
        FocusSession session = focusSessions.create(
                courseId, chapterId, startedAt, endedAt, durationSeconds, status, notes == null ? "" : notes.trim());
        notifyChange(ModelChangeType.FOCUS_SESSION);
        return session;
    }

    public List<FocusSession> getFocusSessions(long studyPlanId) {
        return focusSessions.findByStudyPlan(studyPlanId);
    }

    public AnalyticsSummary getAnalyticsSummary(long studyPlanId, LocalDate from, LocalDate to,
                                                ProgressCalculationStrategy strategy) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Choose a valid analytics date range.");
        }
        PlanSummary summary = summarize(studyPlanId, strategy);
        double expected = getExpectedProgress(studyPlanId, to);
        long focusSeconds = 0;
        for (FocusSession session : getFocusSessions(studyPlanId)) {
            LocalDate sessionDate = session.startedAt().toLocalDate();
            if (!sessionDate.isBefore(from) && !sessionDate.isAfter(to)
                    && session.status() == FocusSessionStatus.COMPLETED) {
                focusSeconds += session.durationSeconds();
            }
        }
        int completed = 0;
        for (Assignment assignment : getAssignments(studyPlanId)) {
            if (assignment.status() == AssignmentStatus.COMPLETED) {
                completed++;
            }
        }
        int overdue = getOverdueAssignments(studyPlanId, to).size();
        return new AnalyticsSummary(summary.progress(), expected, focusSeconds, completed, overdue);
    }

    public List<CourseRisk> getCourseRisks(long studyPlanId, LocalDate date) {
        StudyPlan plan = getStudyPlan(studyPlanId);
        List<CourseRisk> risks = new ArrayList<>();
        for (Course course : getCourses(plan.id())) {
            List<Chapter> courseChapters = getChapters(course.id());
            double totalWeight = 0.0;
            double actualWeight = 0.0;
            double expectedWeight = 0.0;
            for (Chapter chapter : courseChapters) {
                totalWeight += chapter.weight();
                actualWeight += chapter.progress() * chapter.weight();
                LocalDate target = chapter.targetDate() == null ? plan.endDate() : chapter.targetDate();
                expectedWeight += expectedChapterProgress(plan.startDate(), target, date) * chapter.weight();
            }
            double actual = totalWeight == 0.0 ? 0.0 : actualWeight / totalWeight;
            double expected = totalWeight == 0.0 ? 0.0 : expectedWeight / totalWeight;
            risks.add(new CourseRisk(course.id(), course.code(), course.name(), actual, expected));
        }
        return risks;
    }

    private void validatePlanDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Choose both a start date and an end date.");
        }
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("The end date must be after the start date.");
        }
    }

    private void validateChapter(long courseId, Long ignoredChapterId, double weight,
                                 LocalDate targetDate, StudyPlan plan) {
        validatePercentage(weight, "Chapter weight");
        if (weight == 0.0) {
            throw new IllegalArgumentException("Chapter weight must be greater than zero.");
        }
        if (targetDate != null && (targetDate.isBefore(plan.startDate()) || targetDate.isAfter(plan.endDate()))) {
            throw new IllegalArgumentException("The target date must be inside the study-plan dates.");
        }
        double existingWeight = 0.0;
        for (Chapter chapter : getChapters(courseId)) {
            if (ignoredChapterId == null || chapter.id() != ignoredChapterId) {
                existingWeight += chapter.weight();
            }
        }
        if (existingWeight + weight > 100.0001) {
            throw new IllegalArgumentException("Chapter weights for a course cannot exceed 100%.");
        }
    }

    private void validatePercentage(double value, String fieldName) {
        if (Double.isNaN(value) || value < 0.0 || value > 100.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 100.");
        }
    }

    private String cleanCode(String code) {
        return requireText(code, "Course code").toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    // Observer pattern: every interested screen receives a change notification after a model mutation.
    private void notifyChange(ModelChangeType changeType) {
        List<ModelChangeListener> snapshot = new ArrayList<>(listeners);
        for (ModelChangeListener listener : snapshot) {
            listener.onModelChanged(changeType);
        }
    }
}
