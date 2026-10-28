package edu.smartcampus.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class JdbcDatabase implements AutoCloseable {
    private final Connection connection;

    public JdbcDatabase() {
        this(System.getenv().getOrDefault("CAMPUS_DB_URL", "jdbc:sqlite:data/campus.db"));
    }

    public JdbcDatabase(String url) {
        try {
            if (url.startsWith("jdbc:sqlite:") && !url.equals("jdbc:sqlite::memory:")) {
                String path = url.substring("jdbc:sqlite:".length());
                if (!path.isBlank() && !path.startsWith("file:")) {
                    java.nio.file.Path databasePath = java.nio.file.Path.of(path);
                    if (databasePath.getParent() != null) {
                        java.nio.file.Files.createDirectories(databasePath.getParent());
                    }
                }
            }
            connection = DriverManager.getConnection(url);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
            }
        } catch (SQLException | java.io.IOException exception) {
            throw new IllegalStateException("Could not open the campus database: " + exception.getMessage(), exception);
        }
    }

    public void initializeSchema() {
        List<String> statements = List.of(
                "CREATE TABLE IF NOT EXISTS students (id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL, program TEXT NOT NULL, year INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS faculty (id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL, department TEXT NOT NULL, designation TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS courses (id TEXT PRIMARY KEY, name TEXT NOT NULL, faculty_id TEXT NOT NULL, credits INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS resources (id TEXT PRIMARY KEY, type TEXT NOT NULL, name TEXT NOT NULL, capacity INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS activities (id TEXT PRIMARY KEY, name TEXT NOT NULL, date TEXT NOT NULL, location TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS bookings (id TEXT PRIMARY KEY, resource_id TEXT NOT NULL, booked_by TEXT NOT NULL, start_time TEXT NOT NULL, end_time TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS course_enrollments (id TEXT PRIMARY KEY, course_id TEXT NOT NULL REFERENCES courses(id) ON DELETE CASCADE, student_id TEXT NOT NULL REFERENCES students(id) ON DELETE CASCADE, UNIQUE(course_id, student_id))"
        );
        synchronized (connection) {
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not initialize the campus database: " + exception.getMessage(), exception);
            }
        }
    }

    Connection connection() {
        return connection;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not close the campus database: " + exception.getMessage(), exception);
        }
    }
}
