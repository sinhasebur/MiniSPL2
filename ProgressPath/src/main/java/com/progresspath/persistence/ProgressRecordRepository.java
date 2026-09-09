package com.progresspath.persistence;

import com.progresspath.model.ProgressRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Repository pattern: this class contains only progress-history persistence code. */
public final class ProgressRecordRepository {
    private final DatabaseManager databaseManager;

    public ProgressRecordRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void create(long chapterId, double previousProgress, double newProgress, LocalDateTime recordedAt) {
        String sql = """
                INSERT INTO progress_records(chapter_id, previous_progress, new_progress, recorded_at)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chapterId);
            statement.setDouble(2, previousProgress);
            statement.setDouble(3, newProgress);
            statement.setString(4, recordedAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Could not save progress history.", exception);
        }
    }

    public List<ProgressRecord> findByChapter(long chapterId) {
        String sql = """
                SELECT id, chapter_id, previous_progress, new_progress, recorded_at
                FROM progress_records
                WHERE chapter_id = ?
                ORDER BY recorded_at DESC, id DESC
                """;
        List<ProgressRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chapterId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    records.add(new ProgressRecord(
                            rows.getLong("id"),
                            rows.getLong("chapter_id"),
                            rows.getDouble("previous_progress"),
                            rows.getDouble("new_progress"),
                            LocalDateTime.parse(rows.getString("recorded_at"))
                    ));
                }
            }
            return records;
        } catch (SQLException exception) {
            throw new DatabaseException("Could not load progress history.", exception);
        }
    }
}
