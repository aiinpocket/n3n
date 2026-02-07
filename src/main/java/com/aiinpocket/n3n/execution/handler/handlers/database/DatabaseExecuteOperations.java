package com.aiinpocket.n3n.execution.handler.handlers.database;

import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;

/**
 * Database execute (modification) operations: insert, insertMany, update, delete.
 * <p>
 * These operations modify data and return affected row counts.
 */
@Slf4j
final class DatabaseExecuteOperations {

    private DatabaseExecuteOperations() {}

    static NodeExecutionResult execute(
            Connection conn,
            String operation,
            Map<String, Object> params,
            String dbType,
            ObjectMapper objectMapper
    ) throws Exception {
        return switch (operation) {
            case "insert" -> executeInsert(conn, params, dbType, objectMapper);
            case "insertMany" -> executeInsertMany(conn, params, objectMapper);
            case "update" -> executeUpdate(conn, params, objectMapper);
            case "delete" -> executeDelete(conn, params, objectMapper);
            default -> NodeExecutionResult.failure("Unknown execute operation: " + operation);
        };
    }

    private static NodeExecutionResult executeInsert(
            Connection conn,
            Map<String, Object> params,
            String dbType,
            ObjectMapper objectMapper
    ) throws Exception {
        String table = DatabaseSqlUtils.getRequiredParam(params, "table");
        String dataJson = DatabaseSqlUtils.getRequiredParam(params, "data");
        boolean returning = DatabaseSqlUtils.getBoolParam(params, "returning", false);

        if (!DatabaseSqlUtils.isValidIdentifier(table)) {
            return NodeExecutionResult.failure("Invalid table name: " + table);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.readValue(dataJson, Map.class);

        if (data.isEmpty()) {
            return NodeExecutionResult.failure("Data cannot be empty");
        }

        // Build INSERT statement
        List<String> columns = new ArrayList<>(data.keySet());
        List<Object> values = new ArrayList<>(data.values());

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(DatabaseSqlUtils.quoteIdentifier(conn, table)).append(" (");
        sql.append(String.join(", ", columns.stream().map(c -> DatabaseSqlUtils.quoteIdentifier(conn, c)).toList()));
        sql.append(") VALUES (");
        sql.append(String.join(", ", columns.stream().map(c -> "?").toList()));
        sql.append(")");

        if (returning && (dbType.equals("postgresql") || dbType.equals("postgres"))) {
            sql.append(" RETURNING *");
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString(),
                returning ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {

            DatabaseSqlUtils.setParameters(stmt, values.toArray());

            if (returning && (dbType.equals("postgresql") || dbType.equals("postgres"))) {
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Map<String, Object>> rows = ResultSetSerializer.toList(rs, 1);
                    return NodeExecutionResult.success(Map.of(
                            "affectedRows", 1,
                            "insertedRow", rows.isEmpty() ? null : rows.get(0)
                    ));
                }
            } else {
                int affectedRows = stmt.executeUpdate();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("affectedRows", affectedRows);

                // Try to get generated keys
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        result.put("generatedId", keys.getObject(1));
                    }
                } catch (SQLException e) {
                    // Ignore - not all databases support this
                }

                return NodeExecutionResult.success(result);
            }
        }
    }

    private static NodeExecutionResult executeInsertMany(
            Connection conn,
            Map<String, Object> params,
            ObjectMapper objectMapper
    ) throws Exception {
        String table = DatabaseSqlUtils.getRequiredParam(params, "table");
        String dataJson = DatabaseSqlUtils.getRequiredParam(params, "data");

        if (!DatabaseSqlUtils.isValidIdentifier(table)) {
            return NodeExecutionResult.failure("Invalid table name: " + table);
        }

        List<Map<String, Object>> rows = objectMapper.readValue(dataJson, new TypeReference<>() {});

        if (rows.isEmpty()) {
            return NodeExecutionResult.success(Map.of("affectedRows", 0));
        }

        // Get columns from first row
        List<String> columns = new ArrayList<>(rows.get(0).keySet());

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(DatabaseSqlUtils.quoteIdentifier(conn, table)).append(" (");
        sql.append(String.join(", ", columns.stream().map(c -> DatabaseSqlUtils.quoteIdentifier(conn, c)).toList()));
        sql.append(") VALUES (");
        sql.append(String.join(", ", columns.stream().map(c -> "?").toList()));
        sql.append(")");

        int totalAffected = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (Map<String, Object> row : rows) {
                Object[] values = columns.stream().map(row::get).toArray();
                DatabaseSqlUtils.setParameters(stmt, values);
                stmt.addBatch();
            }

            int[] results = stmt.executeBatch();
            for (int r : results) {
                if (r > 0) totalAffected += r;
                else if (r == Statement.SUCCESS_NO_INFO) totalAffected++;
            }
        }

        return NodeExecutionResult.success(Map.of(
                "affectedRows", totalAffected,
                "rowCount", rows.size()
        ));
    }

    private static NodeExecutionResult executeUpdate(
            Connection conn,
            Map<String, Object> params,
            ObjectMapper objectMapper
    ) throws Exception {
        String table = DatabaseSqlUtils.getRequiredParam(params, "table");
        String dataJson = DatabaseSqlUtils.getRequiredParam(params, "data");
        String where = DatabaseSqlUtils.getRequiredParam(params, "where");
        String whereParamsJson = DatabaseSqlUtils.getParam(params, "whereParams", "");

        if (!DatabaseSqlUtils.isValidIdentifier(table)) {
            return NodeExecutionResult.failure("Invalid table name: " + table);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.readValue(dataJson, Map.class);

        if (data.isEmpty()) {
            return NodeExecutionResult.failure("Data cannot be empty");
        }

        // Build UPDATE statement
        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            setClauses.add(DatabaseSqlUtils.quoteIdentifier(conn, entry.getKey()) + " = ?");
            values.add(entry.getValue());
        }

        String sql = "UPDATE " + DatabaseSqlUtils.quoteIdentifier(conn, table) + " SET " +
                String.join(", ", setClauses) + " WHERE " + where;

        // Process WHERE parameters
        Object[] whereParams = DatabaseSqlUtils.parseParams(objectMapper, whereParamsJson, where);
        String processedSql = DatabaseSqlUtils.processNamedParams(sql, whereParamsJson);

        // Combine data values and where params
        Object[] allParams = new Object[values.size() + whereParams.length];
        for (int i = 0; i < values.size(); i++) {
            allParams[i] = values.get(i);
        }
        System.arraycopy(whereParams, 0, allParams, values.size(), whereParams.length);

        try (PreparedStatement stmt = conn.prepareStatement(processedSql)) {
            DatabaseSqlUtils.setParameters(stmt, allParams);
            int affectedRows = stmt.executeUpdate();

            return NodeExecutionResult.success(Map.of("affectedRows", affectedRows));
        }
    }

    private static NodeExecutionResult executeDelete(
            Connection conn,
            Map<String, Object> params,
            ObjectMapper objectMapper
    ) throws Exception {
        String table = DatabaseSqlUtils.getRequiredParam(params, "table");
        String where = DatabaseSqlUtils.getRequiredParam(params, "where");
        String paramsJson = DatabaseSqlUtils.getParam(params, "params", "");

        if (!DatabaseSqlUtils.isValidIdentifier(table)) {
            return NodeExecutionResult.failure("Invalid table name: " + table);
        }

        String sql = "DELETE FROM " + DatabaseSqlUtils.quoteIdentifier(conn, table) + " WHERE " + where;
        Object[] sqlParams = DatabaseSqlUtils.parseParams(objectMapper, paramsJson, sql);
        String processedSql = DatabaseSqlUtils.processNamedParams(sql, paramsJson);

        try (PreparedStatement stmt = conn.prepareStatement(processedSql)) {
            DatabaseSqlUtils.setParameters(stmt, sqlParams);
            int affectedRows = stmt.executeUpdate();

            return NodeExecutionResult.success(Map.of("affectedRows", affectedRows));
        }
    }
}
