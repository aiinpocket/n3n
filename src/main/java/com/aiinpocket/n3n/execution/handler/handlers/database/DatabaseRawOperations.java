package com.aiinpocket.n3n.execution.handler.handlers.database;

import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;

/**
 * Database raw SQL operations: execute (auto-detect), batch.
 * <p>
 * These operations execute arbitrary SQL, auto-detecting whether
 * each statement is a query or a modification.
 */
@Slf4j
final class DatabaseRawOperations {

    private DatabaseRawOperations() {}

    static NodeExecutionResult execute(
            Connection conn,
            String operation,
            Map<String, Object> params,
            ObjectMapper objectMapper
    ) throws Exception {
        return switch (operation) {
            case "execute" -> executeRaw(conn, params, objectMapper);
            case "batch" -> executeBatch(conn, params, objectMapper);
            default -> NodeExecutionResult.failure("Unknown raw operation: " + operation);
        };
    }

    private static NodeExecutionResult executeRaw(
            Connection conn,
            Map<String, Object> params,
            ObjectMapper objectMapper
    ) throws Exception {
        String sql = DatabaseSqlUtils.getRequiredParam(params, "sql");
        String paramsJson = DatabaseSqlUtils.getParam(params, "params", "");
        int maxRows = DatabaseSqlUtils.getIntParam(params, "maxRows", 1000);

        Object[] sqlParams = DatabaseSqlUtils.parseParams(objectMapper, paramsJson, sql);
        String processedSql = DatabaseSqlUtils.processNamedParams(sql, paramsJson);

        boolean isQuery = ResultSetSerializer.isQuery(sql);

        try (PreparedStatement stmt = conn.prepareStatement(processedSql)) {
            DatabaseSqlUtils.setParameters(stmt, sqlParams);

            if (isQuery) {
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Map<String, Object>> rows = ResultSetSerializer.toList(rs, maxRows);

                    return NodeExecutionResult.success(Map.of(
                            "rows", rows,
                            "rowCount", rows.size(),
                            "type", "query"
                    ));
                }
            } else {
                int affectedRows = stmt.executeUpdate();
                String modificationType = ResultSetSerializer.getModificationType(sql);

                return NodeExecutionResult.success(Map.of(
                        "affectedRows", affectedRows,
                        "type", modificationType
                ));
            }
        }
    }

    private static NodeExecutionResult executeBatch(
            Connection conn,
            Map<String, Object> params,
            ObjectMapper objectMapper
    ) throws Exception {
        String statementsJson = DatabaseSqlUtils.getRequiredParam(params, "statements");
        boolean stopOnError = DatabaseSqlUtils.getBoolParam(params, "stopOnError", true);

        List<String> statements = objectMapper.readValue(statementsJson, new TypeReference<>() {});

        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int errorCount = 0;

        for (int i = 0; i < statements.size(); i++) {
            String sql = statements.get(i);
            Map<String, Object> statementResult = new LinkedHashMap<>();
            statementResult.put("index", i);
            statementResult.put("sql", sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (ResultSetSerializer.isQuery(sql)) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        List<Map<String, Object>> rows = ResultSetSerializer.toList(rs, 1000);
                        statementResult.put("success", true);
                        statementResult.put("rowCount", rows.size());
                    }
                } else {
                    int affected = stmt.executeUpdate();
                    statementResult.put("success", true);
                    statementResult.put("affectedRows", affected);
                }
                successCount++;
            } catch (SQLException e) {
                statementResult.put("success", false);
                statementResult.put("error", e.getMessage());
                errorCount++;

                if (stopOnError) {
                    results.add(statementResult);
                    break;
                }
            }

            results.add(statementResult);
        }

        return NodeExecutionResult.success(Map.of(
                "results", results,
                "successCount", successCount,
                "errorCount", errorCount,
                "totalStatements", statements.size()
        ));
    }
}
