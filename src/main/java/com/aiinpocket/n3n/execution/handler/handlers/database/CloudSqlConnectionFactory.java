package com.aiinpocket.n3n.execution.handler.handlers.database;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.ConnectorRegistry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating Cloud SQL connections with proper authentication.
 *
 * Supports:
 * - Service Account JSON key (provided as string content)
 * - Application Default Credentials (when running on GCP)
 * - IAM Database Authentication
 *
 * Each unique credential gets its own connector instance to ensure isolation.
 */
@Component
@Slf4j
public class CloudSqlConnectionFactory {

    /**
     * Cache of data sources by credential hash
     */
    private final ConcurrentHashMap<String, DataSourceEntry> dataSources = new ConcurrentHashMap<>();

    /**
     * TTL for cached data sources (5 minutes)
     */
    private static final long TTL_MS = 5 * 60 * 1000;

    /**
     * Create a Cloud SQL connection.
     *
     * @param dbType             Database type: cloudsql-postgres, cloudsql-mysql, cloudsql-sqlserver
     * @param instanceConnection Instance connection name: project:region:instance
     * @param database           Database name
     * @param username           Database username
     * @param password           Database password (can be empty for IAM auth)
     * @param serviceAccountJson Service account JSON key content (can be null for ADC)
     * @param enableIamAuth      Whether to use IAM authentication
     * @return Database connection
     */
    public Connection getConnection(
        String dbType,
        String instanceConnection,
        String database,
        String username,
        String password,
        String serviceAccountJson,
        boolean enableIamAuth
    ) throws SQLException {
        String cacheKey = generateCacheKey(dbType, instanceConnection, database, username, serviceAccountJson);

        DataSourceEntry entry = dataSources.compute(cacheKey, (key, existing) -> {
            if (existing != null && !existing.dataSource.isClosed()
                && System.currentTimeMillis() - existing.createdAt < TTL_MS) {
                return existing;
            }

            // Close old data source if exists
            if (existing != null && !existing.dataSource.isClosed()) {
                try {
                    existing.dataSource.close();
                } catch (Exception e) {
                    log.warn("Error closing old data source: {}", e.getMessage());
                }
            }

            try {
                return createDataSource(dbType, instanceConnection, database, username, password,
                    serviceAccountJson, enableIamAuth);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create Cloud SQL data source: " + e.getMessage(), e);
            }
        });

        return entry.dataSource.getConnection();
    }

    private DataSourceEntry createDataSource(
        String dbType,
        String instanceConnection,
        String database,
        String username,
        String password,
        String serviceAccountJson,
        boolean enableIamAuth
    ) throws IOException {
        log.info("Creating Cloud SQL data source for instance: {}", instanceConnection);

        // Build JDBC URL based on database type
        String jdbcUrl = buildJdbcUrl(dbType, instanceConnection, database, enableIamAuth);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);

        if (username != null && !username.isEmpty()) {
            config.setUsername(username);
        }
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }

        // Pool configuration
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(1);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(240000);
        config.setConnectionTimeout(30000);

        // Set up Google credentials if service account JSON is provided
        if (serviceAccountJson != null && !serviceAccountJson.isEmpty()) {
            setupCredentials(config, serviceAccountJson, instanceConnection, dbType);
        }

        // Set driver class
        String driverClass = getDriverClass(dbType);
        if (driverClass != null) {
            config.setDriverClassName(driverClass);
        }

        HikariDataSource dataSource = new HikariDataSource(config);
        return new DataSourceEntry(dataSource, System.currentTimeMillis());
    }

    private void setupCredentials(
        HikariConfig config,
        String serviceAccountJson,
        String instanceConnection,
        String dbType
    ) throws IOException {
        // Parse the service account JSON to create credentials
        GoogleCredentials credentials = ServiceAccountCredentials.fromStream(
            new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8))
        );

        // Generate a unique connector name for this credential
        String connectorName = "n3n-" + UUID.randomUUID().toString().substring(0, 8);

        // Register the connector with these specific credentials
        ConnectorConfig connectorConfig = new ConnectorConfig.Builder()
            .withGoogleCredentials(credentials)
            .build();

        ConnectorRegistry.register(connectorName, connectorConfig);

        // Add the connector name to the JDBC URL properties
        // This tells the socket factory which connector to use
        switch (dbType.toLowerCase()) {
            case "cloudsql-postgres", "cloudsql-postgresql" -> {
                config.addDataSourceProperty("cloudSqlNamedConnector", connectorName);
            }
            case "cloudsql-mysql" -> {
                config.addDataSourceProperty("cloudSqlNamedConnector", connectorName);
            }
            case "cloudsql-sqlserver" -> {
                config.addDataSourceProperty("cloudSqlNamedConnector", connectorName);
            }
        }

        log.debug("Registered Cloud SQL connector: {} for instance: {}", connectorName, instanceConnection);
    }

    private String buildJdbcUrl(String dbType, String instanceConnection, String database, boolean enableIamAuth) {
        StringBuilder url = new StringBuilder();

        switch (dbType.toLowerCase()) {
            case "cloudsql-postgres", "cloudsql-postgresql" -> {
                url.append("jdbc:postgresql:///").append(database);
                url.append("?cloudSqlInstance=").append(instanceConnection);
                url.append("&socketFactory=com.google.cloud.sql.postgres.SocketFactory");
                if (enableIamAuth) {
                    url.append("&enableIamAuth=true");
                }
            }
            case "cloudsql-mysql" -> {
                url.append("jdbc:mysql:///").append(database);
                url.append("?cloudSqlInstance=").append(instanceConnection);
                url.append("&socketFactory=com.google.cloud.sql.mysql.SocketFactory");
                url.append("&useSSL=false");
                if (enableIamAuth) {
                    url.append("&enableIamAuth=true");
                }
            }
            case "cloudsql-sqlserver" -> {
                url.append("jdbc:sqlserver://localhost"); // Socket factory handles the actual connection
                url.append(";databaseName=").append(database);
                url.append(";socketFactoryClass=com.google.cloud.sql.sqlserver.SocketFactory");
                url.append(";socketFactoryConstructorArg=").append(instanceConnection);
                url.append(";encrypt=false;trustServerCertificate=true");
            }
            default -> throw new IllegalArgumentException("Unsupported Cloud SQL type: " + dbType);
        }

        return url.toString();
    }

    private String getDriverClass(String dbType) {
        return switch (dbType.toLowerCase()) {
            case "cloudsql-postgres", "cloudsql-postgresql" -> "org.postgresql.Driver";
            case "cloudsql-mysql" -> "com.mysql.cj.jdbc.Driver";
            case "cloudsql-sqlserver" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default -> null;
        };
    }

    private String generateCacheKey(String dbType, String instance, String database, String username, String saJson) {
        // Use hash of service account JSON to avoid storing the full key in memory
        int saHash = saJson != null ? saJson.hashCode() : 0;
        return String.format("%s:%s:%s:%s:%d", dbType, instance, database, username, saHash);
    }

    /**
     * Close all cached data sources (for shutdown).
     */
    public void shutdown() {
        dataSources.forEach((key, entry) -> {
            try {
                if (!entry.dataSource.isClosed()) {
                    entry.dataSource.close();
                }
            } catch (Exception e) {
                log.warn("Error closing Cloud SQL data source: {}", e.getMessage());
            }
        });
        dataSources.clear();

        // Shutdown all registered connectors
        try {
            ConnectorRegistry.shutdown();
        } catch (Exception e) {
            log.warn("Error shutting down Cloud SQL connectors: {}", e.getMessage());
        }
    }

    /**
     * Clean up expired data sources.
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        dataSources.entrySet().removeIf(entry -> {
            if (now - entry.getValue().createdAt > TTL_MS) {
                try {
                    entry.getValue().dataSource.close();
                } catch (Exception e) {
                    log.warn("Error closing expired Cloud SQL data source: {}", e.getMessage());
                }
                return true;
            }
            return false;
        });
    }

    private static class DataSourceEntry {
        final HikariDataSource dataSource;
        final long createdAt;

        DataSourceEntry(HikariDataSource dataSource, long createdAt) {
            this.dataSource = dataSource;
            this.createdAt = createdAt;
        }
    }
}
