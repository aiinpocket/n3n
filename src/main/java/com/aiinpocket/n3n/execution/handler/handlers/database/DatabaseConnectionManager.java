package com.aiinpocket.n3n.execution.handler.handlers.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages database connection pools with automatic cleanup.
 *
 * Key features:
 * - Connection pools cached by credential/connection string hash
 * - Automatic cleanup of idle pools (5 minutes TTL)
 * - Each pool is isolated and short-lived
 * - Supports multiple database types dynamically
 */
@Component
@Slf4j
public class DatabaseConnectionManager {

    /**
     * Connection pool cache: key = hash of connection params
     */
    private final ConcurrentHashMap<String, PoolEntry> pools = new ConcurrentHashMap<>();

    /**
     * Cleanup scheduler for idle pools
     */
    private final ScheduledExecutorService cleanupScheduler;

    /**
     * Idle timeout in milliseconds (5 minutes)
     */
    private static final long IDLE_TIMEOUT_MS = 5 * 60 * 1000;

    /**
     * Cleanup interval (1 minute)
     */
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000;

    public DatabaseConnectionManager() {
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "db-pool-cleaner");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic cleanup
        cleanupScheduler.scheduleAtFixedRate(
            this::cleanupIdlePools,
            CLEANUP_INTERVAL_MS,
            CLEANUP_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Get a connection for the given database configuration.
     *
     * @param dbType   Database type (postgresql, mysql, mariadb, sqlserver, sqlite)
     * @param host     Database host
     * @param port     Database port
     * @param database Database name
     * @param username Username
     * @param password Password
     * @param options  Additional connection options
     * @return Database connection
     */
    public Connection getConnection(
        String dbType,
        String host,
        int port,
        String database,
        String username,
        String password,
        Map<String, String> options
    ) throws SQLException {
        String poolKey = generatePoolKey(dbType, host, port, database, username);
        String jdbcUrl = buildJdbcUrl(dbType, host, port, database, options);

        PoolEntry entry = pools.compute(poolKey, (key, existing) -> {
            if (existing != null && !existing.dataSource.isClosed()) {
                existing.lastAccess = System.currentTimeMillis();
                return existing;
            }
            // Create new pool
            return createPoolEntry(jdbcUrl, username, password, dbType);
        });

        return entry.dataSource.getConnection();
    }

    /**
     * Get a connection using a raw JDBC URL.
     */
    public Connection getConnection(String jdbcUrl, String username, String password) throws SQLException {
        String dbType = detectDbType(jdbcUrl);
        String poolKey = generatePoolKey(jdbcUrl, username);

        PoolEntry entry = pools.compute(poolKey, (key, existing) -> {
            if (existing != null && !existing.dataSource.isClosed()) {
                existing.lastAccess = System.currentTimeMillis();
                return existing;
            }
            return createPoolEntry(jdbcUrl, username, password, dbType);
        });

        return entry.dataSource.getConnection();
    }

    /**
     * Close a specific pool by key.
     */
    public void closePool(String poolKey) {
        PoolEntry entry = pools.remove(poolKey);
        if (entry != null) {
            closeDataSource(entry.dataSource);
        }
    }

    /**
     * Close all pools (for shutdown).
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down database connection manager...");
        cleanupScheduler.shutdownNow();

        pools.forEach((key, entry) -> {
            closeDataSource(entry.dataSource);
        });
        pools.clear();
    }

    // ==================== Internal Methods ====================

    private PoolEntry createPoolEntry(String jdbcUrl, String username, String password, String dbType) {
        log.info("Creating new connection pool for URL: {}", maskJdbcUrl(jdbcUrl));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);

        if (username != null && !username.isEmpty()) {
            config.setUsername(username);
        }
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }

        // Pool configuration for short-lived, isolated usage
        config.setMaximumPoolSize(5);          // Small pool size
        config.setMinimumIdle(1);              // Keep 1 connection ready
        config.setIdleTimeout(60000);          // 1 minute idle timeout
        config.setMaxLifetime(300000);         // 5 minutes max lifetime
        config.setConnectionTimeout(10000);   // 10 seconds connection timeout
        config.setValidationTimeout(3000);    // 3 seconds validation timeout
        config.setLeakDetectionThreshold(60000); // Leak detection

        // Set driver class based on database type
        String driverClass = getDriverClass(dbType);
        if (driverClass != null) {
            config.setDriverClassName(driverClass);
        }

        // Database-specific optimizations
        switch (dbType.toLowerCase()) {
            case "postgresql" -> {
                config.addDataSourceProperty("prepareThreshold", "0");
                config.addDataSourceProperty("preparedStatementCacheQueries", "0");
            }
            case "mysql", "mariadb" -> {
                config.addDataSourceProperty("cachePrepStmts", "false");
                config.addDataSourceProperty("useServerPrepStmts", "false");
                config.addDataSourceProperty("useLocalSessionState", "true");
            }
        }

        HikariDataSource dataSource = new HikariDataSource(config);
        return new PoolEntry(dataSource, System.currentTimeMillis());
    }

    private String getDriverClass(String dbType) {
        return switch (dbType.toLowerCase()) {
            case "postgresql", "postgres" -> "org.postgresql.Driver";
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "mariadb" -> "org.mariadb.jdbc.Driver";
            case "sqlserver", "mssql" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "sqlite" -> "org.sqlite.JDBC";
            case "oracle" -> "oracle.jdbc.OracleDriver";
            // Cloud SQL uses the same drivers as the underlying database
            case "cloudsql-postgres", "cloudsql-postgresql" -> "org.postgresql.Driver";
            case "cloudsql-mysql" -> "com.mysql.cj.jdbc.Driver";
            case "cloudsql-sqlserver" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default -> null;
        };
    }

    private String buildJdbcUrl(String dbType, String host, int port, String database, Map<String, String> options) {
        StringBuilder url = new StringBuilder();

        switch (dbType.toLowerCase()) {
            case "postgresql", "postgres" -> {
                url.append("jdbc:postgresql://").append(host);
                if (port > 0) url.append(":").append(port);
                url.append("/").append(database);
            }
            case "mysql" -> {
                url.append("jdbc:mysql://").append(host);
                if (port > 0) url.append(":").append(port);
                url.append("/").append(database);
                // Default options for MySQL
                if (options == null || !options.containsKey("serverTimezone")) {
                    url.append("?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true");
                }
            }
            case "mariadb" -> {
                url.append("jdbc:mariadb://").append(host);
                if (port > 0) url.append(":").append(port);
                url.append("/").append(database);
            }
            case "sqlserver", "mssql" -> {
                url.append("jdbc:sqlserver://").append(host);
                if (port > 0) url.append(":").append(port);
                url.append(";databaseName=").append(database);
                url.append(";encrypt=false;trustServerCertificate=true");
            }
            case "sqlite" -> {
                // For SQLite, database is the file path
                url.append("jdbc:sqlite:").append(database);
            }
            case "oracle" -> {
                // Oracle supports multiple URL formats:
                // 1. SID format: jdbc:oracle:thin:@host:port:SID
                // 2. Service name format: jdbc:oracle:thin:@//host:port/service_name
                // 3. TNS format: jdbc:oracle:thin:@(DESCRIPTION=...)
                if (database.startsWith("(")) {
                    // TNS format
                    url.append("jdbc:oracle:thin:@").append(database);
                } else if (database.contains("/")) {
                    // Already has service name format indicator
                    url.append("jdbc:oracle:thin:@//").append(host);
                    if (port > 0) url.append(":").append(port);
                    url.append("/").append(database);
                } else {
                    // Default to service name format (modern Oracle)
                    url.append("jdbc:oracle:thin:@//").append(host);
                    if (port > 0) url.append(":").append(port);
                    url.append("/").append(database);
                }
            }
            case "cloudsql-postgres", "cloudsql-postgresql" -> {
                // Google Cloud SQL for PostgreSQL using socket factory
                // host should be the instance connection name: project:region:instance
                url.append("jdbc:postgresql:///").append(database);
                url.append("?cloudSqlInstance=").append(host);
                url.append("&socketFactory=com.google.cloud.sql.postgres.SocketFactory");
                // Enable IAM authentication if no password provided
                if (options != null && "true".equals(options.get("enableIamAuth"))) {
                    url.append("&enableIamAuth=true");
                }
            }
            case "cloudsql-mysql" -> {
                // Google Cloud SQL for MySQL using socket factory
                // host should be the instance connection name: project:region:instance
                url.append("jdbc:mysql:///").append(database);
                url.append("?cloudSqlInstance=").append(host);
                url.append("&socketFactory=com.google.cloud.sql.mysql.SocketFactory");
                url.append("&useSSL=false");
                if (options != null && "true".equals(options.get("enableIamAuth"))) {
                    url.append("&enableIamAuth=true");
                }
            }
            case "cloudsql-sqlserver" -> {
                // Google Cloud SQL for SQL Server
                // host should be the instance connection name: project:region:instance
                url.append("jdbc:sqlserver://localhost");
                url.append(";databaseName=").append(database);
                url.append(";socketFactoryClass=com.google.cloud.sql.sqlserver.SocketFactory");
                url.append(";socketFactoryConstructorArg=").append(host);
                url.append(";encrypt=false;trustServerCertificate=true");
            }
            default -> throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }

        // Append additional options (skip for Cloud SQL which already has query params)
        if (options != null && !options.isEmpty()) {
            // Skip internal options
            Set<String> skipOptions = Set.of("enableIamAuth");
            char separator = url.toString().contains("?") ? '&' : '?';
            for (Map.Entry<String, String> entry : options.entrySet()) {
                if (!skipOptions.contains(entry.getKey())) {
                    url.append(separator).append(entry.getKey()).append("=").append(entry.getValue());
                    separator = '&';
                }
            }
        }

        return url.toString();
    }

    private String detectDbType(String jdbcUrl) {
        if (jdbcUrl.startsWith("jdbc:postgresql")) {
            // Check if it's Cloud SQL (has socketFactory parameter)
            if (jdbcUrl.contains("socketFactory=com.google.cloud.sql.postgres")) {
                return "cloudsql-postgres";
            }
            return "postgresql";
        }
        if (jdbcUrl.startsWith("jdbc:mysql")) {
            if (jdbcUrl.contains("socketFactory=com.google.cloud.sql.mysql")) {
                return "cloudsql-mysql";
            }
            return "mysql";
        }
        if (jdbcUrl.startsWith("jdbc:mariadb")) return "mariadb";
        if (jdbcUrl.startsWith("jdbc:sqlserver")) {
            if (jdbcUrl.contains("socketFactoryClass=com.google.cloud.sql.sqlserver")) {
                return "cloudsql-sqlserver";
            }
            return "sqlserver";
        }
        if (jdbcUrl.startsWith("jdbc:sqlite")) return "sqlite";
        if (jdbcUrl.startsWith("jdbc:oracle")) return "oracle";
        return "unknown";
    }

    private String generatePoolKey(String dbType, String host, int port, String database, String username) {
        return String.format("%s:%s:%d:%s:%s", dbType, host, port, database, username);
    }

    private String generatePoolKey(String jdbcUrl, String username) {
        return String.format("%s:%s", jdbcUrl.hashCode(), username);
    }

    private String maskJdbcUrl(String jdbcUrl) {
        // Mask password in URL if present
        return jdbcUrl.replaceAll("password=[^&]*", "password=***");
    }

    private void closeDataSource(HikariDataSource dataSource) {
        try {
            if (!dataSource.isClosed()) {
                dataSource.close();
            }
        } catch (Exception e) {
            log.warn("Error closing data source: {}", e.getMessage());
        }
    }

    private void cleanupIdlePools() {
        long now = System.currentTimeMillis();
        pools.entrySet().removeIf(entry -> {
            if (now - entry.getValue().lastAccess > IDLE_TIMEOUT_MS) {
                log.info("Closing idle connection pool: {}", entry.getKey());
                closeDataSource(entry.getValue().dataSource);
                return true;
            }
            return false;
        });
    }

    /**
     * Pool entry with last access timestamp.
     */
    private static class PoolEntry {
        final HikariDataSource dataSource;
        volatile long lastAccess;

        PoolEntry(HikariDataSource dataSource, long lastAccess) {
            this.dataSource = dataSource;
            this.lastAccess = lastAccess;
        }
    }
}
