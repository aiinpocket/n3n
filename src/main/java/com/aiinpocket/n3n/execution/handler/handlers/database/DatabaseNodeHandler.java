package com.aiinpocket.n3n.execution.handler.handlers.database;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

/**
 * Universal Database node handler supporting multiple database types.
 *
 * Features:
 * - Supports PostgreSQL, MySQL, MariaDB, SQL Server, SQLite, Oracle
 * - Supports Google Cloud SQL (PostgreSQL, MySQL, SQL Server)
 * - Dynamic connection based on credential configuration
 * - Connection pool with automatic cleanup (5 min TTL)
 * - Parameterized queries (prevent SQL injection)
 * - Proper result serialization to JSON
 * - Distinct handling of queries (SELECT) vs modifications (INSERT/UPDATE/DELETE)
 *
 * Supported database types:
 * - postgresql, mysql, mariadb, sqlserver, sqlite, oracle
 * - cloudsql-postgres, cloudsql-mysql, cloudsql-sqlserver
 *
 * Credential schema (Standard databases):
 * - type: database type (postgresql, mysql, mariadb, sqlserver, sqlite, oracle)
 * - host: database host
 * - port: database port (optional, uses defaults)
 * - database: database name (or Oracle service name)
 * - username: username
 * - password: password
 * - jdbcUrl: (optional) raw JDBC URL, overrides above
 *
 * Credential schema (Cloud SQL):
 * - type: database type (cloudsql-postgres, cloudsql-mysql, cloudsql-sqlserver)
 * - instanceConnection: Cloud SQL instance connection name (project:region:instance)
 * - database: database name
 * - username: database username
 * - password: database password (can be empty for IAM auth)
 * - serviceAccountJson: Service account JSON key content (required for authentication)
 * - enableIamAuth: (optional) use IAM database authentication instead of password
 *
 * Operation logic is delegated to:
 * - {@link DatabaseQueryOperations} - select, selectOne, count
 * - {@link DatabaseExecuteOperations} - insert, insertMany, update, delete
 * - {@link DatabaseRawOperations} - execute, batch
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseNodeHandler extends MultiOperationNodeHandler {

    private final DatabaseConnectionManager connectionManager;
    private final CloudSqlConnectionFactory cloudSqlConnectionFactory;
    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "database";
    }

    @Override
    public String getDisplayName() {
        return "Database";
    }

    @Override
    public String getDescription() {
        return "Query and modify databases. Supports PostgreSQL, MySQL, MariaDB, SQL Server, SQLite, Oracle, and Google Cloud SQL.";
    }

    @Override
    public String getCategory() {
        return "Data";
    }

    @Override
    public String getIcon() {
        return "database";
    }

    @Override
    public String getCredentialType() {
        return "database";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("query", ResourceDef.of("query", "Query", "Execute SELECT queries"));
        resources.put("execute", ResourceDef.of("execute", "Execute", "Execute INSERT/UPDATE/DELETE statements"));
        resources.put("raw", ResourceDef.of("raw", "Raw SQL", "Execute any SQL (auto-detects query vs modification)"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Query operations
        operations.put("query", List.of(
            OperationDef.create("select", "Select")
                .description("Execute a SELECT query and return rows")
                .fields(List.of(
                    FieldDef.textarea("sql", "SQL Query")
                        .withDescription("SELECT query. Use ? for positional params or :name for named params")
                        .withPlaceholder("SELECT * FROM users WHERE status = :status")
                        .required(),
                    FieldDef.textarea("params", "Parameters")
                        .withDescription("Query parameters as JSON object or array")
                        .withPlaceholder("{\"status\": \"active\"} or [\"active\"]"),
                    FieldDef.integer("maxRows", "Max Rows")
                        .withDefault(1000)
                        .withRange(1, 100000)
                        .withDescription("Maximum rows to return"),
                    FieldDef.bool("includeMetadata", "Include Metadata")
                        .withDefault(false)
                        .withDescription("Include column metadata in result")
                ))
                .outputDescription("Returns { rows: [...], rowCount: n, columns?: [...] }")
                .build(),

            OperationDef.create("selectOne", "Select One")
                .description("Execute a SELECT query and return first row only")
                .fields(List.of(
                    FieldDef.textarea("sql", "SQL Query")
                        .withDescription("SELECT query. Use ? for positional params or :name for named params")
                        .withPlaceholder("SELECT * FROM users WHERE id = :id")
                        .required(),
                    FieldDef.textarea("params", "Parameters")
                        .withDescription("Query parameters as JSON object or array")
                        .withPlaceholder("{\"id\": 123}")
                ))
                .outputDescription("Returns single row object or null")
                .build(),

            OperationDef.create("count", "Count")
                .description("Execute a COUNT query")
                .fields(List.of(
                    FieldDef.string("table", "Table")
                        .withDescription("Table name to count")
                        .withPlaceholder("users")
                        .required(),
                    FieldDef.textarea("where", "WHERE Clause")
                        .withDescription("Optional WHERE condition (without WHERE keyword)")
                        .withPlaceholder("status = 'active' AND created_at > '2024-01-01'")
                ))
                .outputDescription("Returns { count: n }")
                .build()
        ));

        // Execute (modification) operations
        operations.put("execute", List.of(
            OperationDef.create("insert", "Insert")
                .description("Insert a row into a table")
                .fields(List.of(
                    FieldDef.string("table", "Table")
                        .withDescription("Table name")
                        .withPlaceholder("users")
                        .required(),
                    FieldDef.textarea("data", "Data")
                        .withDescription("Row data as JSON object")
                        .withPlaceholder("{\"name\": \"John\", \"email\": \"john@example.com\"}")
                        .required(),
                    FieldDef.bool("returning", "Return Inserted Row")
                        .withDefault(false)
                        .withDescription("Return the inserted row (PostgreSQL only)")
                ))
                .outputDescription("Returns { affectedRows: n, insertedRow?: {...} }")
                .build(),

            OperationDef.create("insertMany", "Insert Many")
                .description("Insert multiple rows into a table")
                .fields(List.of(
                    FieldDef.string("table", "Table")
                        .withDescription("Table name")
                        .withPlaceholder("users")
                        .required(),
                    FieldDef.textarea("data", "Data")
                        .withDescription("Array of row objects")
                        .withPlaceholder("[{\"name\": \"John\"}, {\"name\": \"Jane\"}]")
                        .required()
                ))
                .outputDescription("Returns { affectedRows: n }")
                .build(),

            OperationDef.create("update", "Update")
                .description("Update rows in a table")
                .fields(List.of(
                    FieldDef.string("table", "Table")
                        .withDescription("Table name")
                        .required(),
                    FieldDef.textarea("data", "Data")
                        .withDescription("Fields to update as JSON object")
                        .withPlaceholder("{\"status\": \"inactive\"}")
                        .required(),
                    FieldDef.textarea("where", "WHERE Clause")
                        .withDescription("WHERE condition (required for safety)")
                        .withPlaceholder("id = 123")
                        .required(),
                    FieldDef.textarea("whereParams", "WHERE Parameters")
                        .withDescription("Parameters for WHERE clause")
                        .withPlaceholder("{\"id\": 123}")
                ))
                .outputDescription("Returns { affectedRows: n }")
                .build(),

            OperationDef.create("delete", "Delete")
                .description("Delete rows from a table")
                .fields(List.of(
                    FieldDef.string("table", "Table")
                        .withDescription("Table name")
                        .required(),
                    FieldDef.textarea("where", "WHERE Clause")
                        .withDescription("WHERE condition (required for safety)")
                        .withPlaceholder("id = :id")
                        .required(),
                    FieldDef.textarea("params", "Parameters")
                        .withDescription("Parameters for WHERE clause")
                        .withPlaceholder("{\"id\": 123}")
                ))
                .outputDescription("Returns { affectedRows: n }")
                .build()
        ));

        // Raw SQL operations
        operations.put("raw", List.of(
            OperationDef.create("execute", "Execute SQL")
                .description("Execute any SQL statement (auto-detects query vs modification)")
                .fields(List.of(
                    FieldDef.textarea("sql", "SQL")
                        .withDescription("SQL statement to execute")
                        .withPlaceholder("SELECT * FROM users WHERE id = ?")
                        .required(),
                    FieldDef.textarea("params", "Parameters")
                        .withDescription("Query parameters as JSON object or array")
                        .withPlaceholder("[123]"),
                    FieldDef.integer("maxRows", "Max Rows")
                        .withDefault(1000)
                        .withRange(1, 100000)
                        .withDescription("Maximum rows to return (for SELECT)")
                ))
                .outputDescription("Returns rows for SELECT, affectedRows for modifications")
                .build(),

            OperationDef.create("batch", "Batch Execute")
                .description("Execute multiple SQL statements in sequence")
                .fields(List.of(
                    FieldDef.textarea("statements", "SQL Statements")
                        .withDescription("Array of SQL statements as JSON")
                        .withPlaceholder("[\"INSERT INTO log VALUES (...)\", \"UPDATE counter SET ...\"]")
                        .required(),
                    FieldDef.bool("stopOnError", "Stop on Error")
                        .withDefault(true)
                        .withDescription("Stop execution on first error")
                ))
                .outputDescription("Returns { results: [...], successCount: n, errorCount: n }")
                .build()
        ));

        return operations;
    }

    @Override
    public NodeExecutionResult executeOperation(
        NodeExecutionContext context,
        String resource,
        String operation,
        Map<String, Object> credential,
        Map<String, Object> params
    ) {
        // Get database connection info from credential
        String dbType = getCredentialValue(credential, "type");
        if (dbType == null) {
            dbType = getCredentialValue(credential, "dbType");
        }
        if (dbType == null) {
            dbType = "postgresql"; // default
        }

        try (Connection conn = getConnection(credential, dbType)) {
            return switch (resource) {
                case "query" -> DatabaseQueryOperations.execute(conn, operation, params, objectMapper);
                case "execute" -> DatabaseExecuteOperations.execute(conn, operation, params, dbType, objectMapper);
                case "raw" -> DatabaseRawOperations.execute(conn, operation, params, objectMapper);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (SQLException e) {
            log.error("Database error: {} - {}", e.getSQLState(), e.getMessage());
            return NodeExecutionResult.failure("Database error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Database operation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Database operation failed: " + e.getMessage());
        }
    }

    // ==================== Connection Management ====================

    private Connection getConnection(Map<String, Object> credential, String dbType) throws SQLException {
        String jdbcUrl = getCredentialValue(credential, "jdbcUrl");

        // Check if this is a Cloud SQL connection
        if (isCloudSqlType(dbType)) {
            return getCloudSqlConnection(credential, dbType);
        }

        if (jdbcUrl != null && !jdbcUrl.isEmpty()) {
            // Use raw JDBC URL
            String username = getCredentialValue(credential, "username");
            String password = getCredentialValue(credential, "password");
            return connectionManager.getConnection(jdbcUrl, username, password);
        }

        // Build connection from parts
        String host = getCredentialValue(credential, "host");
        String portStr = getCredentialValue(credential, "port");
        String database = getCredentialValue(credential, "database");
        String username = getCredentialValue(credential, "username");
        String password = getCredentialValue(credential, "password");

        if (host == null || host.isEmpty()) {
            host = "localhost";
        }

        int port = getDefaultPort(dbType);
        if (portStr != null && !portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                // Use default
            }
        }

        if (database == null || database.isEmpty()) {
            throw new SQLException("Database name is required");
        }

        return connectionManager.getConnection(dbType, host, port, database, username, password, null);
    }

    /**
     * Check if the database type is a Cloud SQL variant.
     */
    private boolean isCloudSqlType(String dbType) {
        if (dbType == null) return false;
        String lower = dbType.toLowerCase();
        return lower.startsWith("cloudsql-") || lower.equals("cloudsql");
    }

    /**
     * Get a connection for Cloud SQL using service account JSON authentication.
     *
     * Expected credential fields:
     * - instanceConnection: Cloud SQL instance connection name (project:region:instance)
     * - database: Database name
     * - username: Database username
     * - password: Database password (optional for IAM auth)
     * - serviceAccountJson: Service account JSON key content
     * - enableIamAuth: Whether to use IAM database authentication
     */
    private Connection getCloudSqlConnection(Map<String, Object> credential, String dbType) throws SQLException {
        // Instance connection name (project:region:instance)
        String instanceConnection = getCredentialValue(credential, "instanceConnection");
        if (instanceConnection == null || instanceConnection.isEmpty()) {
            // Try 'host' as fallback (for backward compatibility)
            instanceConnection = getCredentialValue(credential, "host");
        }
        if (instanceConnection == null || instanceConnection.isEmpty()) {
            throw new SQLException("Cloud SQL instance connection name is required (format: project:region:instance)");
        }

        // Validate instance connection format
        if (!instanceConnection.matches("^[^:]+:[^:]+:[^:]+$")) {
            throw new SQLException("Invalid Cloud SQL instance connection name. Expected format: project:region:instance");
        }

        String database = getCredentialValue(credential, "database");
        if (database == null || database.isEmpty()) {
            throw new SQLException("Database name is required");
        }

        String username = getCredentialValue(credential, "username");
        String password = getCredentialValue(credential, "password");

        // Service account JSON - this is the key for Cloud SQL authentication
        String serviceAccountJson = getCredentialValue(credential, "serviceAccountJson");
        // If not found, try alternative field names
        if (serviceAccountJson == null || serviceAccountJson.isEmpty()) {
            serviceAccountJson = getCredentialValue(credential, "serviceAccount");
        }
        if (serviceAccountJson == null || serviceAccountJson.isEmpty()) {
            serviceAccountJson = getCredentialValue(credential, "googleCredentials");
        }

        // IAM authentication flag
        boolean enableIamAuth = false;
        String iamAuthValue = getCredentialValue(credential, "enableIamAuth");
        if (iamAuthValue != null) {
            enableIamAuth = "true".equalsIgnoreCase(iamAuthValue) || "1".equals(iamAuthValue);
        }

        log.debug("Connecting to Cloud SQL: type={}, instance={}, database={}, username={}, hasServiceAccount={}, iamAuth={}",
            dbType, instanceConnection, database, username,
            serviceAccountJson != null && !serviceAccountJson.isEmpty(),
            enableIamAuth);

        return cloudSqlConnectionFactory.getConnection(
            dbType,
            instanceConnection,
            database,
            username,
            password,
            serviceAccountJson,
            enableIamAuth
        );
    }

    private int getDefaultPort(String dbType) {
        return switch (dbType.toLowerCase()) {
            case "postgresql", "postgres", "cloudsql-postgres", "cloudsql-postgresql" -> 5432;
            case "mysql", "cloudsql-mysql" -> 3306;
            case "mariadb" -> 3306;
            case "sqlserver", "mssql", "cloudsql-sqlserver" -> 1433;
            case "oracle" -> 1521;
            default -> 0;
        };
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
