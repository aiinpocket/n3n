package com.aiinpocket.n3n.execution.handler.handlers.integrations;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

/**
 * Handler for PostgreSQL database operations.
 */
@Component
@Slf4j
public class PostgresNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "postgres";
    }

    @Override
    public String getDisplayName() {
        return "PostgreSQL";
    }

    @Override
    public String getDescription() {
        return "Execute queries on PostgreSQL databases.";
    }

    @Override
    public String getCategory() {
        return "Database";
    }

    @Override
    public String getIcon() {
        return "database";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "query");
        String host = getStringConfig(context, "host", "localhost");
        int port = getIntConfig(context, "port", 5432);
        String database = getStringConfig(context, "database", "");
        String username = getStringConfig(context, "username", "");
        String password = getStringConfig(context, "password", "");
        String query = getStringConfig(context, "query", "");

        if (database.isEmpty()) {
            return NodeExecutionResult.failure("Database name is required");
        }

        if (query.isEmpty()) {
            return NodeExecutionResult.failure("SQL query is required");
        }

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            return switch (operation) {
                case "query" -> executeQuery(conn, query, context);
                case "execute" -> executeUpdate(conn, query, context);
                case "insert" -> executeInsert(conn, query, context);
                default -> NodeExecutionResult.failure("Unknown operation: " + operation);
            };
        } catch (SQLException e) {
            log.error("PostgreSQL error: {}", e.getMessage());
            return NodeExecutionResult.failure("Database error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeQuery(Connection conn, String query, NodeExecutionContext context) throws SQLException {
        // Get parameters for prepared statement
        List<Object> params = (List<Object>) context.getNodeConfig().getOrDefault("params", List.of());

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            setParameters(stmt, params);

            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }

                Map<String, Object> output = new HashMap<>();
                output.put("rows", rows);
                output.put("rowCount", rows.size());
                output.put("columns", getColumnNames(meta));

                log.debug("PostgreSQL query returned {} rows", rows.size());
                return NodeExecutionResult.success(output);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeUpdate(Connection conn, String query, NodeExecutionContext context) throws SQLException {
        List<Object> params = (List<Object>) context.getNodeConfig().getOrDefault("params", List.of());

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            setParameters(stmt, params);
            int affected = stmt.executeUpdate();

            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("affectedRows", affected);

            log.debug("PostgreSQL update affected {} rows", affected);
            return NodeExecutionResult.success(output);
        }
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeInsert(Connection conn, String query, NodeExecutionContext context) throws SQLException {
        List<Object> params = (List<Object>) context.getNodeConfig().getOrDefault("params", List.of());

        try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            setParameters(stmt, params);
            int affected = stmt.executeUpdate();

            List<Object> generatedKeys = new ArrayList<>();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                while (keys.next()) {
                    generatedKeys.add(keys.getObject(1));
                }
            }

            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("affectedRows", affected);
            output.put("generatedKeys", generatedKeys);

            log.debug("PostgreSQL insert affected {} rows", affected);
            return NodeExecutionResult.success(output);
        }
    }

    private void setParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
        }
    }

    private List<String> getColumnNames(ResultSetMetaData meta) throws SQLException {
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            columns.add(meta.getColumnLabel(i));
        }
        return columns;
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        Object database = config.get("database");
        if (database == null || database.toString().trim().isEmpty()) {
            return ValidationResult.invalid("database", "Database name is required");
        }

        Object query = config.get("query");
        if (query == null || query.toString().trim().isEmpty()) {
            return ValidationResult.invalid("query", "SQL query is required");
        }

        return ValidationResult.valid();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("database", "query"),
            "properties", Map.of(
                "operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "enum", List.of("query", "execute", "insert"),
                    "default", "query",
                    "description", "query: SELECT, execute: UPDATE/DELETE, insert: INSERT"
                ),
                "host", Map.of(
                    "type", "string",
                    "title", "Host",
                    "default", "localhost"
                ),
                "port", Map.of(
                    "type", "integer",
                    "title", "Port",
                    "default", 5432
                ),
                "database", Map.of(
                    "type", "string",
                    "title", "Database",
                    "description", "Database name"
                ),
                "username", Map.of(
                    "type", "string",
                    "title", "Username"
                ),
                "password", Map.of(
                    "type", "string",
                    "title", "Password",
                    "format", "password"
                ),
                "query", Map.of(
                    "type", "string",
                    "title", "SQL Query",
                    "description", "SQL query to execute. Use $1, $2, etc. for parameters.",
                    "format", "code",
                    "language", "sql"
                ),
                "params", Map.of(
                    "type", "array",
                    "title", "Parameters",
                    "description", "Query parameters (for prepared statements)",
                    "items", Map.of("type", "string")
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }
}
