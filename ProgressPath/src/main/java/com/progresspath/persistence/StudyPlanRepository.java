package com.progresspath.persistence;

import com.progresspath.model.StudyPlan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StudyPlanRepository {
    private final DatabaseManager databaseManager;

    public StudyPlanRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public StudyPlan create(String name, LocalDate startDate, LocalDate endDate) {
        String sql = "INSERT INTO study_plans(name, start_date, end_date) VALUES (?, ?, ?)";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, startDate.toString());
            statement.setString(3, endDate.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new StudyPlan(keys.getLong(1), name, startDate, endDate, false);
                }
            }
            throw new SQLException("No key was returned for the new study plan.");
        } catch (SQLException exception) {
            throw new DatabaseException("Could not save the study plan. Its name may already exist.", exception);
        }
    }

    public List<StudyPlan> findAll() {
        String sql = "SELECT id, name, start_date, end_date, archived FROM study_plans ORDER BY archived, created_at, id";
        List<StudyPlan> plans = new ArrayList<>();
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                plans.add(map(rows));
            }
            return plans;
        } catch (SQLException exception) {
            throw new DatabaseException("Could not load study plans.", exception);
        }
    }

    public Optional<StudyPlan> findById(long id) {
        String sql = "SELECT id, name, start_date, end_date, archived FROM study_plans WHERE id = ?";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not load the study plan.", exception);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM study_plans WHERE id = ?";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Could not delete the study plan.", exception);
        }
    }

    public void update(long id, String name, LocalDate startDate, LocalDate endDate) {
        String sql = "UPDATE study_plans SET name = ?, start_date = ?, end_date = ? WHERE id = ?";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, startDate.toString());
            statement.setString(3, endDate.toString());
            statement.setLong(4, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Could not update the study plan. Its name may already exist.", exception);
        }
    }

    public void setArchived(long id, boolean archived) {
        String sql = "UPDATE study_plans SET archived = ? WHERE id = ?";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, archived ? 1 : 0);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Could not change the plan archive status.", exception);
        }
    }

    private StudyPlan map(ResultSet row) throws SQLException {
        return new StudyPlan(
                row.getLong("id"),
                row.getString("name"),
                LocalDate.parse(row.getString("start_date")),
                LocalDate.parse(row.getString("end_date")),
                row.getInt("archived") == 1
        );
    }
}
