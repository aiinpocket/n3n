package com.aiinpocket.n3n.credential.service;

import com.aiinpocket.n3n.credential.dto.ConnectionTestResult;
import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Service for testing credential connections to various databases
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionTestService {

    private final CredentialService credentialService;
    private final CredentialRepository credentialRepository;
    private final ObjectMapper objectMapper;

    private static final int CONNECTION_TIMEOUT_SECONDS = 10;

    /**
     * Test an unsaved credential connection
     */
    public ConnectionTestResult testConnection(String type, Map<String, Object> data) {
        long startTime = System.currentTimeMillis();

        try {
            return switch (type.toLowerCase()) {
                case "mongodb" -> testMongoDBConnection(data, startTime);
                case "postgres", "postgresql" -> testPostgreSQLConnection(data, startTime);
                case "mysql", "mariadb" -> testMySQLConnection(data, startTime);
                case "redis" -> testRedisConnection(data, startTime);
                case "database" -> testGenericDatabaseConnection(data, startTime);
                default -> ConnectionTestResult.failure(
                        "Unsupported credential type: " + type,
                        System.currentTimeMillis() - startTime
                );
            };
        } catch (Exception e) {
            log.error("Connection test failed for type {}: {}", type, e.getMessage(), e);
            return ConnectionTestResult.failure(
                    "Connection failed: " + e.getMessage(),
                    System.currentTimeMillis() - startTime
            );
        }
    }

    /**
     * Test a saved credential connection and update metadata
     */
    public ConnectionTestResult testSavedCredential(UUID credentialId, UUID userId) {
        // Get decrypted credential data
        Map<String, Object> data = credentialService.getDecryptedData(credentialId, userId);

        // Get credential entity to get the type
        Credential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found: " + credentialId));

        // Test the connection
        ConnectionTestResult result = testConnection(credential.getType(), data);

        // Update credential metadata with test result
        updateCredentialMetadata(credential, result);

        return result;
    }

    private void updateCredentialMetadata(Credential credential, ConnectionTestResult result) {
        try {
            Map<String, Object> metadata = credential.getMetadata();
            if (metadata == null) {
                metadata = new HashMap<>();
            }

            Map<String, Object> lastTest = new HashMap<>();
            lastTest.put("success", result.isSuccess());
            lastTest.put("message", result.getMessage());
            lastTest.put("testedAt", result.getTestedAt().toString());
            lastTest.put("latencyMs", result.getLatencyMs());
            if (result.getServerVersion() != null) {
                lastTest.put("serverVersion", result.getServerVersion());
            }

            metadata.put("lastTest", lastTest);
            credential.setMetadata(metadata);
            credentialRepository.save(credential);
        } catch (Exception e) {
            log.warn("Failed to update credential metadata: {}", e.getMessage());
        }
    }

    // ==================== MongoDB ====================

    private ConnectionTestResult testMongoDBConnection(Map<String, Object> data, long startTime) {
        MongoClient client = null;
        try {
            // Check if using connection string
            String connectionString = getStringValue(data, "connectionString");
            MongoClientSettings settings;

            if (connectionString != null && !connectionString.isBlank()) {
                settings = MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(connectionString))
                        .applyToSocketSettings(builder ->
                                builder.connectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .applyToClusterSettings(builder ->
                                builder.serverSelectionTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .build();
            } else {
                // Build from individual fields
                String host = getStringValue(data, "host", "localhost");
                int port = getIntValue(data, "port", 27017);
                String database = getStringValue(data, "database");
                String username = getStringValue(data, "username");
                String password = getStringValue(data, "password");
                String authSource = getStringValue(data, "authSource", "admin");

                if (database == null || database.isBlank()) {
                    return ConnectionTestResult.failure(
                            "Database name is required",
                            System.currentTimeMillis() - startTime
                    );
                }

                MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                        .applyToClusterSettings(builder ->
                                builder.hosts(List.of(new ServerAddress(host, port)))
                                        .serverSelectionTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .applyToSocketSettings(builder ->
                                builder.connectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));

                if (username != null && !username.isBlank() && password != null) {
                    MongoCredential credential = MongoCredential.createCredential(
                            username, authSource, password.toCharArray());
                    settingsBuilder.credential(credential);
                }

                settings = settingsBuilder.build();
            }

            client = MongoClients.create(settings);

            // Get database name from connection string or data
            String dbName = getStringValue(data, "database");
            if (dbName == null || dbName.isBlank()) {
                if (connectionString != null) {
                    ConnectionString cs = new ConnectionString(connectionString);
                    dbName = cs.getDatabase();
                }
            }

            if (dbName == null || dbName.isBlank()) {
                dbName = "admin";
            }

            MongoDatabase db = client.getDatabase(dbName);

            // Test with ping command
            Document pingResult = db.runCommand(new Document("ping", 1));
            Document serverStatus = db.runCommand(new Document("serverStatus", 1));
            String version = serverStatus.getString("version");

            long latency = System.currentTimeMillis() - startTime;
            return ConnectionTestResult.success(
                    "MongoDB connection successful",
                    latency,
                    version != null ? "MongoDB " + version : null
            );

        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.debug("Error closing MongoDB client: {}", e.getMessage());
                }
            }
        }
    }

    // ==================== PostgreSQL ====================

    private ConnectionTestResult testPostgreSQLConnection(Map<String, Object> data, long startTime) {
        return testSQLConnection(data, startTime, "postgresql", "org.postgresql.Driver");
    }

    // ==================== MySQL ====================

    private ConnectionTestResult testMySQLConnection(Map<String, Object> data, long startTime) {
        return testSQLConnection(data, startTime, "mysql", "com.mysql.cj.jdbc.Driver");
    }

    // ==================== Generic Database ====================

    private ConnectionTestResult testGenericDatabaseConnection(Map<String, Object> data, long startTime) {
        String dbType = getStringValue(data, "databaseType", "postgres").toLowerCase();
        return switch (dbType) {
            case "mysql", "mariadb" -> testMySQLConnection(data, startTime);
            case "postgres", "postgresql" -> testPostgreSQLConnection(data, startTime);
            case "mssql", "sqlserver" -> testSQLServerConnection(data, startTime);
            case "oracle" -> testOracleConnection(data, startTime);
            default -> ConnectionTestResult.failure(
                    "Unsupported database type: " + dbType,
                    System.currentTimeMillis() - startTime
            );
        };
    }

    private ConnectionTestResult testSQLServerConnection(Map<String, Object> data, long startTime) {
        return testSQLConnection(data, startTime, "sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    private ConnectionTestResult testOracleConnection(Map<String, Object> data, long startTime) {
        String host = getStringValue(data, "host", "localhost");
        int port = getIntValue(data, "port", 1521);
        String database = getStringValue(data, "database");
        String username = getStringValue(data, "username");
        String password = getStringValue(data, "password");

        if (database == null || database.isBlank()) {
            return ConnectionTestResult.failure(
                    "Database/SID is required",
                    System.currentTimeMillis() - startTime
            );
        }

        String jdbcUrl = String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, database);

        return testJdbcConnection(jdbcUrl, username, password, startTime, "oracle.jdbc.OracleDriver");
    }

    private ConnectionTestResult testSQLConnection(Map<String, Object> data, long startTime,
                                                    String dbType, String driverClass) {
        String host = getStringValue(data, "host", "localhost");
        int port = getIntValue(data, "port", getDefaultPort(dbType));
        String database = getStringValue(data, "database");
        String username = getStringValue(data, "username");
        String password = getStringValue(data, "password");
        boolean ssl = getBooleanValue(data, "ssl", false);

        if (database == null || database.isBlank()) {
            return ConnectionTestResult.failure(
                    "Database name is required",
                    System.currentTimeMillis() - startTime
            );
        }

        String jdbcUrl = buildJdbcUrl(dbType, host, port, database, ssl);
        return testJdbcConnection(jdbcUrl, username, password, startTime, driverClass);
    }

    private String buildJdbcUrl(String dbType, String host, int port, String database, boolean ssl) {
        return switch (dbType) {
            case "postgresql" -> String.format("jdbc:postgresql://%s:%d/%s%s",
                    host, port, database, ssl ? "?ssl=true" : "");
            case "mysql" -> String.format("jdbc:mysql://%s:%d/%s%s",
                    host, port, database, ssl ? "?useSSL=true" : "?useSSL=false");
            case "sqlserver" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s%s",
                    host, port, database, ssl ? ";encrypt=true" : "");
            default -> throw new IllegalArgumentException("Unknown database type: " + dbType);
        };
    }

    private int getDefaultPort(String dbType) {
        return switch (dbType) {
            case "postgresql" -> 5432;
            case "mysql" -> 3306;
            case "sqlserver" -> 1433;
            case "oracle" -> 1521;
            default -> 5432;
        };
    }

    private ConnectionTestResult testJdbcConnection(String jdbcUrl, String username, String password,
                                                     long startTime, String driverClass) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        if (username != null && !username.isBlank()) {
            config.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }
        config.setDriverClassName(driverClass);
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_SECONDS * 1000L);
        config.setValidationTimeout(CONNECTION_TIMEOUT_SECONDS * 1000L);

        HikariDataSource dataSource = null;
        Connection connection = null;
        try {
            dataSource = new HikariDataSource(config);
            connection = dataSource.getConnection();

            // Test with simple query
            String serverVersion = null;
            try (Statement stmt = connection.createStatement()) {
                // Get server version
                String versionQuery = getVersionQuery(jdbcUrl);
                try (ResultSet rs = stmt.executeQuery(versionQuery)) {
                    if (rs.next()) {
                        serverVersion = rs.getString(1);
                    }
                }

                // Verify with SELECT 1
                try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    rs.next();
                }
            }

            long latency = System.currentTimeMillis() - startTime;
            String dbName = extractDbType(jdbcUrl);
            return ConnectionTestResult.success(
                    dbName + " connection successful",
                    latency,
                    serverVersion
            );

        } catch (Exception e) {
            return ConnectionTestResult.failure(
                    "Connection failed: " + e.getMessage(),
                    System.currentTimeMillis() - startTime
            );
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    log.debug("Error closing connection: {}", e.getMessage());
                }
            }
            if (dataSource != null) {
                try {
                    dataSource.close();
                } catch (Exception e) {
                    log.debug("Error closing datasource: {}", e.getMessage());
                }
            }
        }
    }

    private String getVersionQuery(String jdbcUrl) {
        if (jdbcUrl.contains("postgresql")) {
            return "SELECT version()";
        } else if (jdbcUrl.contains("mysql")) {
            return "SELECT version()";
        } else if (jdbcUrl.contains("sqlserver")) {
            return "SELECT @@VERSION";
        } else if (jdbcUrl.contains("oracle")) {
            return "SELECT * FROM v$version WHERE BANNER LIKE 'Oracle%'";
        }
        return "SELECT 1";
    }

    private String extractDbType(String jdbcUrl) {
        if (jdbcUrl.contains("postgresql")) return "PostgreSQL";
        if (jdbcUrl.contains("mysql")) return "MySQL";
        if (jdbcUrl.contains("sqlserver")) return "SQL Server";
        if (jdbcUrl.contains("oracle")) return "Oracle";
        return "Database";
    }

    // ==================== Redis ====================

    private ConnectionTestResult testRedisConnection(Map<String, Object> data, long startTime) {
        RedisClient client = null;
        StatefulRedisConnection<String, String> connection = null;

        try {
            String host = getStringValue(data, "host", "localhost");
            int port = getIntValue(data, "port", 6379);
            String password = getStringValue(data, "password");
            int database = getIntValue(data, "database", 0);
            boolean tls = getBooleanValue(data, "tls", false);

            RedisURI.Builder uriBuilder = RedisURI.builder()
                    .withHost(host)
                    .withPort(port)
                    .withDatabase(database)
                    .withTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS));

            if (password != null && !password.isBlank()) {
                uriBuilder.withPassword(password.toCharArray());
            }

            if (tls) {
                uriBuilder.withSsl(true);
            }

            client = RedisClient.create(uriBuilder.build());
            connection = client.connect();

            // Test with PING command
            String pingResult = connection.sync().ping();

            // Get server info
            String info = connection.sync().info("server");
            String version = extractRedisVersion(info);

            long latency = System.currentTimeMillis() - startTime;

            if ("PONG".equals(pingResult)) {
                return ConnectionTestResult.success(
                        "Redis connection successful",
                        latency,
                        version != null ? "Redis " + version : null
                );
            } else {
                return ConnectionTestResult.failure(
                        "Unexpected PING response: " + pingResult,
                        latency
                );
            }

        } catch (Exception e) {
            return ConnectionTestResult.failure(
                    "Connection failed: " + e.getMessage(),
                    System.currentTimeMillis() - startTime
            );
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    log.debug("Error closing Redis connection: {}", e.getMessage());
                }
            }
            if (client != null) {
                try {
                    client.shutdown();
                } catch (Exception e) {
                    log.debug("Error shutting down Redis client: {}", e.getMessage());
                }
            }
        }
    }

    private String extractRedisVersion(String info) {
        if (info == null) return null;
        for (String line : info.split("\n")) {
            if (line.startsWith("redis_version:")) {
                return line.substring("redis_version:".length()).trim();
            }
        }
        return null;
    }

    // ==================== Utility Methods ====================

    private String getStringValue(Map<String, Object> data, String key) {
        return getStringValue(data, key, null);
    }

    private String getStringValue(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private int getIntValue(Map<String, Object> data, String key, int defaultValue) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBooleanValue(Map<String, Object> data, String key, boolean defaultValue) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
