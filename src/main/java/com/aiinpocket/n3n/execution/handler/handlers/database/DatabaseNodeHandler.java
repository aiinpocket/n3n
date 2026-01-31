package com.aiinpocket.n3n.execution.handler.handlers.database;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseNodeHandler extends MultiOperationNodeHandler {

    private final DatabaseConnectionManager connectionManager;
    private final CloudSqlConnectionFactory cloudSqlConnectionFactory;
    private final ObjectMapper objectMapper;

    // Pattern for named parameters :paramName
    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

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
                case "query" -> switch (operation) {
                    case "select" -> executeSelect(conn, params);
                    case "selectOne" -> executeSelectOne(conn, params);
                    case "count" -> executeCount(conn, params);
                    default -> NodeExecutionResult.failure("Unknown query operation: " + operation);
                };
                case "execute" -> switch (operation) {
                    case "insert" -> executeInsert(conn, params, dbType);
                    case "insertMany" -> executeInsertMany(conn, params);
                    case "update" -> executeUpdate(conn, params);
                    case "delete" -> executeDelete(conn, params);
                    default -> NodeExecutionResult.failure("Unknown execute operation: " + operation);
                };
                case "raw" -> switch (operation) {
                    case "execute" -> executeRaw(conn, params);
                    case "batch" -> executeBatch(conn, params);
                    default -> NodeExecutionResult.failure("Unknown raw operation: " + operation);
                };
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

    // ==================== Query Operations ====================

    private NodeExecutionResult executeSelect(Connection conn, Map<String, Object> params) throws Exception {
        String sql = getRequiredParam(params, "sql");
        String paramsJson = getParam(params, "params", "");
        int maxRows = getIntParam(params, "maxRows", 1000);
        boolean includeMetadata = getBoolParam(params, "includeMetadata", false);

        Object[] sqlParams = parseParams(paramsJson, sql);
        String processedSql = processNamedParams(sql, paramsJson);

        try (PreparedStatement stmt = conn.prepareStatement(processedSql)) {
            setParameters(stmt, sqlParams);

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

    private NodeExecutionResult executeSelectOne(Connection conn, Map<String, Object> params) throws Exception {
        String sql = getRequiredParam(params, "sql");
        String paramsJson = getParam(params, "params", "");

        Object[] sqlParams = parseParams(paramsJson, sql);
        String processedSql = processNamedParams(sql, paramsJson);

        try (PreparedStatement stmt = conn.prepareStatement(processedSql)) {
            setParameters(stmt, sqlParams);

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

    private NodeExecutionResult executeCount(Connection conn, Map<String, Object> params) throws Exception {
        String table = getRequiredParam(params, "table");
        String where = getParam(params, "where", "");

        // Validate table name (prevent injection)
        if (!isValidIdentifier(table)) {
            return NodeExecutionResult.failure("Invalid table name: " + table);
        }

        String sql = "SELECT COUNT(*) AS count FROM " + quoteIdentifier(conn, table);
        if (!where.isEmpty()) {
            sql += " WHERE " + where;
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return NodeExecutionResult.success(Map.of("count", rs.getLong(1)));
            }
            return NodeExecutionResult.success(Map.of("count", 0));
        }
    }

    // ==================== Execute (Modification) Operations ====================

    private NodeExecutionResult executeInsert(Connection conn, Map<String, Object> params, String dbType) throws Exception {
        String table = getRequiredParam(params, "table");
        String dataJson = getRequiredParam(params, "data");
        boolean returning = getBoolParam(params, "returning", false);

        if (!isValidIdentifier(table)) {
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
        sql.append("INSERT INTO ").append(quoteIdentifier(conn, table)).append(" (");
        sql.append(String.join(", ", columns.stream().map(c -> quoteIdentifier(conn, c)).toList()));
        sql.append(") VALUES (");
        sql.append(String.join(", ", columns.stream().map(c -> "?").toList()));
        sql.append(")");

        if (returning && (dbType.equals("postgresql") || dbType.equals("postgres"))) {
            sql.append(" RETURNING *");
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString(),
            returning ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {

            setParameters(stmt, values.toArray());

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

    private NodeExecutionResult executeInsertMany(Connection conn, Map<String, Object> params) throws Exception {
        String table = getRequiredParam(params, "table");
        String dataJson = getRequiredParam(params, "data");

        if (!isValidIdentifier(table)) {
            return NodeExecutionResult.failure("Invalid table name: " + table);
        }

        List<Map<String, Object>> rows = objectMapper.readValue(dataJson, new TypeReference<>() {});

        if (rows.isEmpty()) {
            return NodeExecutionResult.success(Map.of("affectedRows", 0));
        }

        // Get columns from first row
        List<String> columns = new ArrayList<>(rows.get(0).keySet());

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(quoteIdentifier(conn, table)).append(" (");
        sql.append(String.join(", ", columns.stream().map(c -> quoteIdentifier(conn, c)).toList()));
        sql.append(") VALUES (");
        sql.append(String.join(", ", columns.stream().map(c -> "?").toList()));
        sql.append(")");

        int totalAffected = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (Map<String, Object> row : rows) {
                Object[] values = columns.stream().map(row::get).toArray();
                setParameters(stmt, values);
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

    private NodeExecutionResult executeUpdate(Connection conn, Map<String, Object> params) throws Exception {
        String table = getRequiredParam(params, "table");
        String dataJson = getRequiredParam(params, "data");
        String where = getRequiredParam(params, "where");
        String whereParamsJson = getParam(params, "whereParams", "");

        if (!isValidIdentifier(table)) {
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
            setClauses.add(quoteIdentifier(conn, entry.getKey()) + " = ?");
            values.add(entry.getValue());
        }

        String sql = "UPDATE " + quoteIdentifier(conn, table) + " SET " +
            String.join(", ", setClauses) + " WHERE " + where;

        // Process WHERE parameters
        Object[] whereParams = parseParams(whereParamsJson, where);
        String processedSql = processNamedParams(sql, whereParamsJson);

        // Combine data values and where params
        Object[] allParams = new Object[values.size() + whereParams.length];
        for (int i = 0; i < values.size(); i++) {
            allParams[i] = values.get(i);
        }
        System.arraycopy(whereParams, 0, allParams, values.size(), whereParams.length);

        try (PreparedStatement stmt = conn.prepareStatement(processedSql)) {
            setParameters(stmt, allParams);
            int affectedRows = stmt.executeUpdate();

            return NodeExecutionResult.success(Map.of("affectedRows", affectedRows));
        }
    }

    private NodeExecutionResult executeDelete(Connection conn, Map<String, Object> params) throws Exception {
        String table = getRequiredParam(params, "table");
        String where = getRequiredParam(params, "where");
        String paramsJson = getParam(params, "params", "");

        if (!isValidIdentifier(table)) {
            return NodeExecutionResult.failure("Invalid table name: " + table);
        }

        String sql = "DELETE FROM " + quoteIdentifier(conn, table) + " WHERE " + where;
        Object[] sqlParams = parseParams(paramsJson, sql);
        String processedSql = processNamedParams(sql, paramsJson);

        try (PreparedStatement stmt = conn.prepareStatement(processedSql)) {
            setParameters(stmt, sqlParams);
            int affectedRows = stmt.executeUpdate();

            return NodeExecutionResult.success(Map.of("affectedRows", affectedRows));
        }
    }

    // ==================== Raw SQL Operations ====================

    private NodeExecutionResult executeRaw(Connection conn, Map<String, Object> params) throws Exception {
        String sql = getRequiredParam(params, "sql");
        String paramsJson = getParam(params, "params", "");
        int maxRows = getIntParam(params, "maxRows", 1000);

        Object[] sqlParams = parseParams(paramsJson, sql);
        String processedSql = processNamedParams(sql, paramsJson);

        boolean isQuery = ResultSetSerializer.isQuery(sql);

        try (PreparedStatement stmt = conn.prepareStatement(processedSql)) {
            setParameters(stmt, sqlParams);

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

    private NodeExecutionResult executeBatch(Connection conn, Map<String, Object> params) throws Exception {
        String statementsJson = getRequiredParam(params, "statements");
        boolean stopOnError = getBoolParam(params, "stopOnError", true);

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

    // ==================== Helper Methods ====================

    private Object[] parseParams(String paramsJson, String sql) throws Exception {
        if (paramsJson == null || paramsJson.isEmpty()) {
            return new Object[0];
        }

        Object parsed = objectMapper.readValue(paramsJson, Object.class);

        if (parsed instanceof List) {
            return ((List<?>) parsed).toArray();
        } else if (parsed instanceof Map) {
            // For named parameters, we need to extract in order
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            List<Object> orderedParams = new ArrayList<>();

            Matcher matcher = NAMED_PARAM_PATTERN.matcher(sql);
            while (matcher.find()) {
                String paramName = matcher.group(1);
                if (map.containsKey(paramName)) {
                    orderedParams.add(map.get(paramName));
                } else {
                    orderedParams.add(null);
                }
            }
            return orderedParams.toArray();
        }

        return new Object[0];
    }

    private String processNamedParams(String sql, String paramsJson) {
        if (paramsJson == null || paramsJson.isEmpty()) {
            return sql;
        }

        // Replace :paramName with ?
        return NAMED_PARAM_PATTERN.matcher(sql).replaceAll("?");
    }

    private void setParameters(PreparedStatement stmt, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            setParameter(stmt, i + 1, params[i]);
        }
    }

    private void setParameter(PreparedStatement stmt, int index, Object value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.NULL);
        } else if (value instanceof String) {
            stmt.setString(index, (String) value);
        } else if (value instanceof Integer) {
            stmt.setInt(index, (Integer) value);
        } else if (value instanceof Long) {
            stmt.setLong(index, (Long) value);
        } else if (value instanceof Double) {
            stmt.setDouble(index, (Double) value);
        } else if (value instanceof Float) {
            stmt.setFloat(index, (Float) value);
        } else if (value instanceof Boolean) {
            stmt.setBoolean(index, (Boolean) value);
        } else if (value instanceof java.util.Date) {
            stmt.setTimestamp(index, new Timestamp(((java.util.Date) value).getTime()));
        } else if (value instanceof byte[]) {
            stmt.setBytes(index, (byte[]) value);
        } else {
            // Convert to string for other types
            stmt.setString(index, value.toString());
        }
    }

    private boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        // Allow alphanumeric, underscore, and dots (for schema.table)
        return identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?$");
    }

    private String quoteIdentifier(Connection conn, String identifier) {
        try {
            String quote = conn.getMetaData().getIdentifierQuoteString();
            if (quote == null || quote.equals(" ")) {
                quote = "\""; // Standard SQL
            }
            // Split on dot for schema.table
            if (identifier.contains(".")) {
                String[] parts = identifier.split("\\.");
                return quote + parts[0] + quote + "." + quote + parts[1] + quote;
            }
            return quote + identifier + quote;
        } catch (SQLException e) {
            return "\"" + identifier + "\"";
        }
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
