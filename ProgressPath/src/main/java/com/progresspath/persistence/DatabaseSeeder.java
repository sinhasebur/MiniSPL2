package com.progresspath.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

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
}
