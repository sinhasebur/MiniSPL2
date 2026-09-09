package com.progresspath.persistence;

import com.progresspath.model.FocusSession;
import com.progresspath.model.FocusSessionStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Repository pattern: focus-session storage is isolated from timer state logic. */
public final class FocusSessionRepository {
    private final DatabaseManager databaseManager;

    public FocusSessionRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public FocusSession create(long courseId, Long chapterId, LocalDateTime startedAt,
                               LocalDateTime endedAt, long durationSeconds,
                               FocusSessionStatus status, String notes) {
        String sql = """
                INSERT INTO focus_sessions(
                    course_id, chapter_id, started_at, ended_at, duration_seconds, status, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, courseId);
            if (chapterId == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setLong(2, chapterId);
            }
            statement.setString(3, startedAt.toString());
            statement.setString(4, endedAt.toString());
            statement.setLong(5, durationSeconds);
            statement.setString(6, status.name());
            statement.setString(7, notes);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new FocusSession(keys.getLong(1), courseId, chapterId, startedAt, endedAt,
                            durationSeconds, status, notes);
                }
            }
            throw new SQLException("No key was returned for the focus session.");
        } catch (SQLException exception) {
            throw new DatabaseException("Could not save the focus session.", exception);
        }
    }

    public List<FocusSession> findByStudyPlan(long studyPlanId) {
        String sql = """
                SELECT f.id, f.course_id, f.chapter_id, f.started_at, f.ended_at,
                       f.duration_seconds, f.status, f.notes
                FROM focus_sessions f
                JOIN courses c ON c.id = f.course_id
                WHERE c.study_plan_id = ?
                ORDER BY f.started_at DESC, f.id DESC
                """;
        List<FocusSession> sessions = new ArrayList<>();
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studyPlanId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    sessions.add(map(rows));
                }
            }
            return sessions;
        } catch (SQLException exception) {
            throw new DatabaseException("Could not load focus sessions.", exception);
        }
    }

    private FocusSession map(ResultSet row) throws SQLException {
        long chapterValue = row.getLong("chapter_id");
        Long chapterId = row.wasNull() ? null : chapterValue;
        return new FocusSession(
                row.getLong("id"),
                row.getLong("course_id"),
                chapterId,
                LocalDateTime.parse(row.getString("started_at")),
                LocalDateTime.parse(row.getString("ended_at")),
                row.getLong("duration_seconds"),
                FocusSessionStatus.valueOf(row.getString("status")),
                row.getString("notes")
        );
    }
}
