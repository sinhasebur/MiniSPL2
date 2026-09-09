package com.progresspath.persistence;

import com.progresspath.model.Course;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CourseRepository {
    private final DatabaseManager databaseManager;

    public CourseRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Course create(long studyPlanId, String code, String name) {
        String sql = "INSERT INTO courses(study_plan_id, code, name) VALUES (?, ?, ?)";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, studyPlanId);
            statement.setString(2, code);
            statement.setString(3, name);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new Course(keys.getLong(1), studyPlanId, code, name);
                }
            }
            throw new SQLException("No key was returned for the new course.");
        } catch (SQLException exception) {
            throw new DatabaseException("Could not save the course. Its code may already exist in this plan.", exception);
        }
    }

    public List<Course> findByStudyPlan(long studyPlanId) {
        String sql = "SELECT id, study_plan_id, code, name FROM courses WHERE study_plan_id = ? ORDER BY code";
        List<Course> courses = new ArrayList<>();
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studyPlanId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    courses.add(map(rows));
                }
            }
            return courses;
        } catch (SQLException exception) {
            throw new DatabaseException("Could not load courses.", exception);
        }
    }

    public Optional<Course> findById(long id) {
        String sql = "SELECT id, study_plan_id, code, name FROM courses WHERE id = ?";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not load the course.", exception);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM courses WHERE id = ?";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Could not delete the course.", exception);
        }
    }

    public void update(long id, String code, String name) {
        String sql = "UPDATE courses SET code = ?, name = ? WHERE id = ?";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, name);
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Could not update the course. Its code may already exist in this plan.", exception);
        }
    }

    private Course map(ResultSet row) throws SQLException {
        return new Course(
                row.getLong("id"),
                row.getLong("study_plan_id"),
                row.getString("code"),
                row.getString("name")
        );
    }
}
