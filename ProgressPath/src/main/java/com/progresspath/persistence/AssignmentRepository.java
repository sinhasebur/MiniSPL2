package com.progresspath.persistence;

import com.progresspath.model.Assignment;
import com.progresspath.model.AssignmentPriority;
import com.progresspath.model.AssignmentStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Repository pattern: controllers never contain assignment SQL. */
public final class AssignmentRepository {
    private final DatabaseManager databaseManager;

    public AssignmentRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Assignment create(long courseId, String title, String description, LocalDate dueDate,
                             AssignmentPriority priority, AssignmentStatus status) {
        String sql = """
                INSERT INTO assignments(course_id, title, description, due_date, priority, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setValues(statement, courseId, title, description, dueDate, priority, status);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new Assignment(keys.getLong(1), courseId, title, description, dueDate, priority, status);
                }
            }
            throw new SQLException("No key was returned for the new assignment.");
        } catch (SQLException exception) {
            throw new DatabaseException("Could not save the assignment.", exception);
        }
    }

    public void update(Assignment assignment) {
        String sql = """
                UPDATE assignments
                SET course_id = ?, title = ?, description = ?, due_date = ?, priority = ?, status = ?
                WHERE id = ?
                """;
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setValues(statement, assignment.courseId(), assignment.title(), assignment.description(),
                    assignment.dueDate(), assignment.priority(), assignment.status());
            statement.setLong(7, assignment.id());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Could not update the assignment.", exception);
        }
    }

    public List<Assignment> findByStudyPlan(long studyPlanId) {
        String sql = """
                SELECT a.id, a.course_id, a.title, a.description, a.due_date, a.priority, a.status
                FROM assignments a
                JOIN courses c ON c.id = a.course_id
                WHERE c.study_plan_id = ?
                ORDER BY a.due_date, a.priority DESC, a.id
                """;
        List<Assignment> assignments = new ArrayList<>();
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studyPlanId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    assignments.add(map(rows));
                }
            }
            return assignments;
        } catch (SQLException exception) {
            throw new DatabaseException("Could not load assignments.", exception);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM assignments WHERE id = ?";
        try (Connection connection = databaseManager.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Could not delete the assignment.", exception);
        }
    }

    private void setValues(PreparedStatement statement, long courseId, String title, String description,
                           LocalDate dueDate, AssignmentPriority priority, AssignmentStatus status)
            throws SQLException {
        statement.setLong(1, courseId);
        statement.setString(2, title);
        statement.setString(3, description);
        statement.setString(4, dueDate.toString());
        statement.setString(5, priority.name());
        statement.setString(6, status.name());
    }

    private Assignment map(ResultSet row) throws SQLException {
        return new Assignment(
                row.getLong("id"),
                row.getLong("course_id"),
                row.getString("title"),
                row.getString("description"),
                LocalDate.parse(row.getString("due_date")),
                AssignmentPriority.valueOf(row.getString("priority")),
                AssignmentStatus.valueOf(row.getString("status"))
        );
    }
}
