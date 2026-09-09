package com.progresspath.persistence;

import com.progresspath.model.Chapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ChapterRepository {
    private final DatabaseManager databaseManager;

    public ChapterRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Chapter create(long courseId, String name, double weight, LocalDate targetDate) {
        String sql = "INSERT INTO chapters(course_id, name, weight, target_date) VALUES (?, ?, ?, ?)";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, courseId);
            statement.setString(2, name);
            statement.setDouble(3, weight);
            statement.setString(4, targetDate == null ? null : targetDate.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new Chapter(keys.getLong(1), courseId, name, weight, 0.0, targetDate);
                }
            }
            throw new SQLException("No key was returned for the new chapter.");
        } catch (SQLException exception) {
            throw new DatabaseException("Could not save the chapter. Its name may already exist in this course.", exception);
        }
    }

    public List<Chapter> findByCourse(long courseId) {
        String sql = """
                SELECT id, course_id, name, weight, progress, target_date
                FROM chapters
                WHERE course_id = ?
                ORDER BY target_date IS NULL, target_date, id
                """;
        return find(sql, courseId);
    }

    public List<Chapter> findByStudyPlan(long studyPlanId) {
        String sql = """
                SELECT ch.id, ch.course_id, ch.name, ch.weight, ch.progress, ch.target_date
                FROM chapters ch
                JOIN courses c ON c.id = ch.course_id
                WHERE c.study_plan_id = ?
                ORDER BY c.code, ch.target_date IS NULL, ch.target_date, ch.id
                """;
        return find(sql, studyPlanId);
    }

    public Optional<Chapter> findById(long id) {
        String sql = "SELECT id, course_id, name, weight, progress, target_date FROM chapters WHERE id = ?";
        List<Chapter> result = find(sql, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public void updateProgress(long id, double progress) {
        String sql = "UPDATE chapters SET progress = ? WHERE id = ?";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, progress);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Could not update chapter progress.", exception);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM chapters WHERE id = ?";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Could not delete the chapter.", exception);
        }
    }

    public void update(long id, String name, double weight, LocalDate targetDate) {
        String sql = "UPDATE chapters SET name = ?, weight = ?, target_date = ? WHERE id = ?";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setDouble(2, weight);
            statement.setString(3, targetDate == null ? null : targetDate.toString());
            statement.setLong(4, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Could not update the chapter. Its name may already exist in this course.", exception);
        }
    }

    private List<Chapter> find(String sql, long parentId) {
        List<Chapter> chapters = new ArrayList<>();
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, parentId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    chapters.add(map(rows));
                }
            }
            return chapters;
        } catch (SQLException exception) {
            throw new DatabaseException("Could not load chapters.", exception);
        }
    }

    private Chapter map(ResultSet row) throws SQLException {
        String targetDate = row.getString("target_date");
        return new Chapter(
                row.getLong("id"),
                row.getLong("course_id"),
                row.getString("name"),
                row.getDouble("weight"),
                row.getDouble("progress"),
                targetDate == null ? null : LocalDate.parse(targetDate)
        );
    }
}
