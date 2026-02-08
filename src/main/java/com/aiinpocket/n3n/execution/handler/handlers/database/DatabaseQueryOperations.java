package com.aiinpocket.n3n.execution.handler.handlers.database;

import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Database query operations: select, selectOne, count.
 * <p>
 * These are read-only operations that return result sets.
 */
@Slf4j
final class DatabaseQueryOperations {

    private DatabaseQueryOperations() {}

    static NodeExecutionResult execute(
            Connection conn,
            String operation,
            Map<String, Object> params,
            ObjectMapper objectMapper
    ) throws Exception {
        return switch (operation) {
            case "select" -> executeSelect(conn, params, objectMapper);
            case "selectOne" -> executeSelectOne(conn, params, objectMapper);
            case "count" -> executeCount(conn, params, objectMapper);
            default -> NodeExecutionResult.failure("Unknown query operation: " + operation);
        };
    }

    private static NodeExecutionResult executeSelect(
            Connection conn,
            Map<String, Object> params,
            ObjectMapper objectMapper
    ) throws Exception {
        String sql = DatabaseSqlUtils.getRequiredParam(params, "sql");
        String paramsJson = DatabaseSqlUtils.getParam(params, "params", "");
        int maxRows = DatabaseSqlUtils.getIntParam(params, "maxRows", 1000);
        boolean includeMetadata = DatabaseSqlUtils.getBoolParam(params, "includeMetadata", false);

        Object[] sqlParams = DatabaseSqlUtils.parseParams(objectMapper, paramsJson, sql);
        String processedSql = DatabaseSqlUtils.processNamedParams(sql, paramsJson);

        try (PreparedStatement stmt = conn.prepareStatement(processedSql)) {
            DatabaseSqlUtils.setParameters(stmt, sqlParams);

            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> rows = ResultSetSerializer.toList(rs, maxRows);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("rows", rows);
                result.put("rowCount", rows.size());

                if (includeMetadata && !rows.isEmpty()) {
                    // Re-execute to get metadata (ResultSet already consumed)
                    try (ResultSet rs2 = stmt.executeQuery()) {
                        result.put("columns", ResultSetSerializer.getColumnMetadata(rs2));
                    }
                }

                log.debug("SELECT returned {} rows", rows.size());
                return NodeExecutionResult.success(result);
            }
        }
    }

    private static NodeExecutionResult executeSelectOne(
            Connection conn,
            Map<String, Object> params,
            ObjectMapper objectMapper
    ) throws Exception {
        String sql = DatabaseSqlUtils.getRequiredParam(params, "sql");
        String paramsJson = DatabaseSqlUtils.getParam(params, "params", "");

        Object[] sqlParams = DatabaseSqlUtils.parseParams(objectMapper, paramsJson, sql);
        String processedSql = DatabaseSqlUtils.processNamedParams(sql, paramsJson);

        try (PreparedStatement stmt = conn.prepareStatement(processedSql)) {
            DatabaseSqlUtils.setParameters(stmt, sqlParams);

            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> rows = ResultSetSerializer.toList(rs, 1);

                if (rows.isEmpty()) {
                    return NodeExecutionResult.success(Map.of(
                            "row", (Object) null,
                            "found", false
                    ));
                }

                return NodeExecutionResult.success(Map.of(
                        "row", rows.get(0),
                        "found", true
                ));
            }
        }
    }

    private static NodeExecutionResult executeCount(
            Connection conn,
            Map<String, Object> params,
            ObjectMapper objectMapper
    ) throws Exception {
        String table = DatabaseSqlUtils.getRequiredParam(params, "table");
        String where = DatabaseSqlUtils.getParam(params, "where", "");
        String paramsJson = DatabaseSqlUtils.getParam(params, "params", "");

        if (!DatabaseSqlUtils.isValidIdentifier(table)) {
            return NodeExecutionResult.failure("Invalid table name: " + table);
        }

        String sql = "SELECT COUNT(*) AS count FROM " + DatabaseSqlUtils.quoteIdentifier(conn, table);
        if (!where.isEmpty()) {
            sql += " WHERE " + where;
        }

        Object[] sqlParams = DatabaseSqlUtils.parseParams(objectMapper, paramsJson, sql);
        String processedSql = DatabaseSqlUtils.processNamedParams(sql, paramsJson);

        try (PreparedStatement stmt = conn.prepareStatement(processedSql)) {
            DatabaseSqlUtils.setParameters(stmt, sqlParams);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return NodeExecutionResult.success(Map.of("count", rs.getLong(1)));
                }
                return NodeExecutionResult.success(Map.of("count", 0));
            }
        }
    }
}
