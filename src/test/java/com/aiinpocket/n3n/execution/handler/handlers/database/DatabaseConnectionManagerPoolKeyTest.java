package com.aiinpocket.n3n.execution.handler.handlers.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the connection-pool cache key incorporates the password (SHA-256), so a caller with a
 * correct host/db/username but a wrong/stale password can never land on another user's pool.
 */
class DatabaseConnectionManagerPoolKeyTest {

    private DatabaseConnectionManager manager;

    @BeforeEach
    void setUp() {
        manager = new DatabaseConnectionManager();
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    @Test
    void poolKey_sameCredentials_produceSameKey() {
        String a = manager.generatePoolKey("postgresql", "db.host", 5432, "app", "alice", "pw1");
        String b = manager.generatePoolKey("postgresql", "db.host", 5432, "app", "alice", "pw1");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void poolKey_differentPassword_producesDifferentKey() {
        String a = manager.generatePoolKey("postgresql", "db.host", 5432, "app", "alice", "correct");
        String b = manager.generatePoolKey("postgresql", "db.host", 5432, "app", "alice", "wrong");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void poolKey_neverContainsPlaintextPassword() {
        String key = manager.generatePoolKey("postgresql", "db.host", 5432, "app", "alice", "SuperSecret");
        assertThat(key).doesNotContain("SuperSecret");
    }

    @Test
    void jdbcUrlPoolKey_differentPassword_producesDifferentKey() {
        String a = manager.generatePoolKey("jdbc:postgresql://db.host:5432/app", "alice", "correct");
        String b = manager.generatePoolKey("jdbc:postgresql://db.host:5432/app", "alice", "wrong");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void jdbcUrlPoolKey_sameInputs_produceSameKey() {
        String a = manager.generatePoolKey("jdbc:postgresql://db.host:5432/app", "alice", "pw");
        String b = manager.generatePoolKey("jdbc:postgresql://db.host:5432/app", "alice", "pw");
        assertThat(a).isEqualTo(b);
    }
}
