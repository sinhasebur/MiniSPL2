package com.progresspath.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {
    private final String jdbcUrl;

    public DatabaseManager() {
        this(defaultDatabasePath());
    }

    public DatabaseManager(Path databasePath) {
        try {
            Path absolutePath = databasePath.toAbsolutePath();
            Path parent = absolutePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.jdbcUrl = "jdbc:sqlite:" + absolutePath;
        } catch (IOException exception) {
            throw new DatabaseException("Could not create the database directory.", exception);
        }
    }

    public Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 3000");
        }
        return connection;
    }

    private static Path defaultDatabasePath() {
        String configuredDirectory = System.getProperty("progresspath.dataDir", "data");
        return Path.of(configuredDirectory, "progresspath.db");
    }
}
