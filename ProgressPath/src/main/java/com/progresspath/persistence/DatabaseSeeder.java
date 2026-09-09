package com.progresspath.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DatabaseSeeder {
    private final DatabaseManager databaseManager;

    public DatabaseSeeder(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void initialize() {
        try (Connection connection = databaseManager.connect();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS study_plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        start_date TEXT NOT NULL,
                        end_date TEXT NOT NULL,
                        archived INTEGER NOT NULL DEFAULT 0 CHECK (archived IN (0, 1)),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CHECK (date(end_date) > date(start_date))
                    )
                    """);
            ensureArchivedColumn(connection);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS courses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        study_plan_id INTEGER NOT NULL,
                        code TEXT NOT NULL,
                        name TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (study_plan_id) REFERENCES study_plans(id) ON DELETE CASCADE,
                        UNIQUE (study_plan_id, code)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS chapters (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        course_id INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        weight REAL NOT NULL CHECK (weight > 0 AND weight <= 100),
                        progress REAL NOT NULL DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
                        target_date TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
                        UNIQUE (course_id, name)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS progress_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        chapter_id INTEGER NOT NULL,
                        previous_progress REAL NOT NULL,
                        new_progress REAL NOT NULL,
                        recorded_at TEXT NOT NULL,
                        FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
                        CHECK (previous_progress BETWEEN 0 AND 100),
                        CHECK (new_progress BETWEEN 0 AND 100)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS assignments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        course_id INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        due_date TEXT NOT NULL,
                        priority TEXT NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
                        status TEXT NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS focus_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        course_id INTEGER NOT NULL,
                        chapter_id INTEGER,
                        started_at TEXT NOT NULL,
                        ended_at TEXT NOT NULL,
                        duration_seconds INTEGER NOT NULL CHECK (duration_seconds >= 0),
                        status TEXT NOT NULL CHECK (status IN ('COMPLETED', 'CANCELLED')),
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
                        FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE SET NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_progress_chapter ON progress_records(chapter_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_assignment_due ON assignments(due_date)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_focus_started ON focus_sessions(started_at)");
        } catch (SQLException exception) {
            throw new DatabaseException("Could not initialize the database.", exception);
        }
    }

    private void ensureArchivedColumn(Connection connection) throws SQLException {
        boolean found = false;
        try (Statement check = connection.createStatement();
             java.sql.ResultSet columns = check.executeQuery("PRAGMA table_info(study_plans)")) {
            while (columns.next()) {
                if ("archived".equals(columns.getString("name"))) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            try (Statement migration = connection.createStatement()) {
                migration.executeUpdate("ALTER TABLE study_plans ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
            }
        }
    }

    /**
     * Inserts a small, realistic demo dataset so the dashboards, reports, and
     * progress calculations have something to render. The method is
     * idempotent: if any {@code study_plans} rows already exist it returns a
     * report with zero inserts and {@code dataAlreadyPresent=true} so the
     * caller can rerun it safely.
     *
     * <p>Demo shape (dates are relative to "today" so the demo never goes
     * stale):</p>
     * <ul>
     *     <li>1 study plan covering a 90-day semester window ending in the future.</li>
     *     <li>2 courses with 4 weighted chapters each (weights sum to 100).</li>
     *     <li>3 progress history records spread across two chapters.</li>
     *     <li>5 assignments (overdue, pending, in-progress, completed, cancelled).</li>
     *     <li>4 focus sessions, two of them completed, one cancelled, one linked to a chapter.</li>
     * </ul>
     */
    public SeedingReport seedDemoData() {
        try (Connection connection = databaseManager.connect()) {
            connection.setAutoCommit(false);
            try {
                if (tableHasRows(connection, "study_plans")) {
                    connection.commit();
                    return new SeedingReport(0, 0, 0, 0, 0, 0, true);
                }

                LocalDate today = LocalDate.now();
                LocalDate planStart = today.minusDays(30);
                LocalDate planEnd = today.plusDays(60);

                long planId = insertPlan(connection, "Fall 2026 — Sample Plan", planStart, planEnd, false);
                long dsCourseId = insertCourse(connection, planId, "CSE301", "Data Structures");
                long algoCourseId = insertCourse(connection, planId, "MAT210", "Discrete Mathematics");

                long dsArrays = insertChapter(connection, dsCourseId, "Arrays & Linked Lists", 25.0, today.plusDays(5));
                long dsStacks = insertChapter(connection, dsCourseId, "Stacks & Queues", 25.0, today.plusDays(20));
                insertChapter(connection, dsCourseId, "Trees & Heaps", 25.0, today.plusDays(40));
                insertChapter(connection, dsCourseId, "Graph Algorithms", 25.0, today.plusDays(60));

                long algoLogic = insertChapter(connection, algoCourseId, "Logic & Proofs", 30.0, today.plusDays(10));
                long algoSets = insertChapter(connection, algoCourseId, "Sets & Relations", 30.0, today.plusDays(25));
                insertChapter(connection, algoCourseId, "Functions & Counting", 20.0, today.plusDays(45));
                insertChapter(connection, algoCourseId, "Graph Theory Basics", 20.0, today.plusDays(60));

                updateChapterProgress(connection, dsArrays, 100.0);
                insertProgressRecord(connection, dsArrays, 0.0, 50.0, today.minusDays(20).atTime(10, 0));
                insertProgressRecord(connection, dsArrays, 50.0, 100.0, today.minusDays(10).atTime(11, 30));

                updateChapterProgress(connection, dsStacks, 60.0);
                insertProgressRecord(connection, dsStacks, 0.0, 60.0, today.minusDays(3).atTime(18, 15));

                updateChapterProgress(connection, algoLogic, 100.0);
                updateChapterProgress(connection, algoSets, 40.0);
                insertProgressRecord(connection, algoSets, 0.0, 40.0, today.minusDays(2).atTime(21, 0));

                int assignments = 0;
                assignments += insertAssignment(connection, dsCourseId,
                        "Linked List Lab", "Implement singly and doubly linked lists.",
                        today.minusDays(5), "HIGH", "PENDING");
                assignments += insertAssignment(connection, dsCourseId,
                        "Stack-based Evaluator", "Evaluate postfix expressions with a stack.",
                        today.plusDays(2), "MEDIUM", "IN_PROGRESS");
                assignments += insertAssignment(connection, dsCourseId,
                        "Binary Tree Traversals", "Recursive and iterative traversals.",
                        today.plusDays(15), "HIGH", "PENDING");
                assignments += insertAssignment(connection, algoCourseId,
                        "Proof Practice Set", "Work through 10 induction proofs.",
                        today.plusDays(7), "MEDIUM", "COMPLETED");
                assignments += insertAssignment(connection, algoCourseId,
                        "Counting Problems", "Permutations and combinations worksheet.",
                        today.minusDays(10), "LOW", "CANCELLED");

                int focusSessions = 0;
                focusSessions += insertFocusSession(connection, dsCourseId, dsArrays,
                        today.minusDays(7).atTime(9, 0), today.minusDays(7).atTime(10, 30),
                        90 * 60, "COMPLETED", "Worked through array examples.");
                focusSessions += insertFocusSession(connection, dsCourseId, dsStacks,
                        today.minusDays(4).atTime(14, 0), today.minusDays(4).atTime(15, 15),
                        75 * 60, "COMPLETED", "Implemented stack-based evaluator.");
                focusSessions += insertFocusSession(connection, algoCourseId, algoSets,
                        today.minusDays(2).atTime(20, 0), today.minusDays(2).atTime(20, 45),
                        45 * 60, "COMPLETED", "Reviewed set operations.");
                focusSessions += insertFocusSession(connection, algoCourseId, null,
                        today.minusDays(1).atTime(22, 0), today.minusDays(1).atTime(22, 10),
                        10 * 60, "CANCELLED", "Got interrupted — will resume tomorrow.");

                connection.commit();
                return new SeedingReport(1, 2, 8, 3, assignments, focusSessions, false);
            } catch (SQLException exception) {
                connection.rollback();
                throw new DatabaseException("Could not seed demo data.", exception);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not open a connection to seed demo data.", exception);
        }
    }

    private static boolean tableHasRows(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return rows.next() && rows.getInt(1) > 0;
        }
    }

    private static long insertPlan(Connection connection, String name, LocalDate start, LocalDate end, boolean archived)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO study_plans(name, start_date, end_date, archived) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, start.toString());
            statement.setString(3, end.toString());
            statement.setInt(4, archived ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert study plan " + name);
    }

    private static long insertCourse(Connection connection, long studyPlanId, String code, String name)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO courses(study_plan_id, code, name) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, studyPlanId);
            statement.setString(2, code);
            statement.setString(3, name);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert course " + code);
    }

    private static long insertChapter(Connection connection, long courseId, String name, double weight, LocalDate targetDate)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO chapters(course_id, name, weight, target_date) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, courseId);
            statement.setString(2, name);
            statement.setDouble(3, weight);
            statement.setString(4, targetDate == null ? null : targetDate.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert chapter " + name);
    }

    private static void updateChapterProgress(Connection connection, long chapterId, double progress)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE chapters SET progress = ? WHERE id = ?")) {
            statement.setDouble(1, progress);
            statement.setLong(2, chapterId);
            statement.executeUpdate();
        }
    }

    private static void insertProgressRecord(Connection connection, long chapterId,
                                             double previousProgress, double newProgress, LocalDateTime recordedAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO progress_records(chapter_id, previous_progress, new_progress, recorded_at) "
                        + "VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, chapterId);
            statement.setDouble(2, previousProgress);
            statement.setDouble(3, newProgress);
            statement.setString(4, recordedAt.toString());
            statement.executeUpdate();
        }
    }

    private static int insertAssignment(Connection connection, long courseId, String title, String description,
                                        LocalDate dueDate, String priority, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assignments(course_id, title, description, due_date, priority, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, courseId);
            statement.setString(2, title);
            statement.setString(3, description);
            statement.setString(4, dueDate.toString());
            statement.setString(5, priority);
            statement.setString(6, status);
            return statement.executeUpdate();
        }
    }

    private static int insertFocusSession(Connection connection, long courseId, Long chapterId,
                                          LocalDateTime startedAt, LocalDateTime endedAt,
                                          long durationSeconds, String status, String notes) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO focus_sessions(course_id, chapter_id, started_at, ended_at, duration_seconds, status, notes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, courseId);
            if (chapterId == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setLong(2, chapterId);
            }
            statement.setString(3, startedAt.toString());
            statement.setString(4, endedAt.toString());
            statement.setLong(5, durationSeconds);
            statement.setString(6, status);
            statement.setString(7, notes);
            return statement.executeUpdate();
        }
    }
}
