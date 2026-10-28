package edu.smartcampus.repository;

import edu.smartcampus.model.Identifiable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class JdbcCrudRepository<T extends Identifiable> {
    private final JdbcDatabase database;
    private final String table;
    private final List<String> columns;
    private final Function<ResultSet, T> mapper;
    private final Function<T, List<Object>> values;

    public JdbcCrudRepository(
            JdbcDatabase database,
            String table,
            List<String> columns,
            Function<ResultSet, T> mapper,
            Function<T, List<Object>> values) {
        this.database = database;
        this.table = table;
        this.columns = List.copyOf(columns);
        this.mapper = mapper;
        this.values = values;
    }

    public void save(T item) {
        String columnList = String.join(", ", columns);
        String placeholders = String.join(", ", java.util.Collections.nCopies(columns.size() + 1, "?"));
        String updates = columns.stream().map(column -> column + " = excluded." + column)
                .collect(java.util.stream.Collectors.joining(", "));
        String sql = "INSERT INTO " + table + " (id, " + columnList + ") VALUES (" + placeholders
                + ") ON CONFLICT(id) DO UPDATE SET " + updates;
        synchronized (database.connection()) {
            try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
                statement.setString(1, item.getId());
                bindValues(statement, values.apply(item), 2);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw databaseError("save", exception);
            }
        }
    }

    public List<T> findAll() {
        synchronized (database.connection()) {
            try (PreparedStatement statement = database.connection().prepareStatement(
                    "SELECT * FROM " + table + " ORDER BY id");
                 ResultSet resultSet = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapper.apply(resultSet));
                }
                return results;
            } catch (SQLException exception) {
                throw databaseError("read", exception);
            }
        }
    }

    public Optional<T> findById(String id) {
        synchronized (database.connection()) {
            try (PreparedStatement statement = database.connection().prepareStatement(
                    "SELECT * FROM " + table + " WHERE id = ?")) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapper.apply(resultSet)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw databaseError("search", exception);
            }
        }
    }

    public boolean delete(String id) {
        synchronized (database.connection()) {
            try (PreparedStatement statement = database.connection().prepareStatement(
                    "DELETE FROM " + table + " WHERE id = ?")) {
                statement.setString(1, id);
                return statement.executeUpdate() > 0;
            } catch (SQLException exception) {
                throw databaseError("delete", exception);
            }
        }
    }

    private static void bindValues(PreparedStatement statement, List<Object> values, int startIndex)
            throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            statement.setObject(startIndex + index, values.get(index));
        }
    }

    private IllegalStateException databaseError(String action, SQLException exception) {
        return new IllegalStateException("Could not " + action + " " + table + ": " + exception.getMessage(), exception);
    }
}
