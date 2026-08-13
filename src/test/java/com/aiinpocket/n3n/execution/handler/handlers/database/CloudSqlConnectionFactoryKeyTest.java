package com.aiinpocket.n3n.execution.handler.handlers.database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Cloud SQL data-source cache key incorporates both the service-account JSON and the
 * DB password via SHA-256 (no plaintext secret held as a key), so a different secret yields a
 * different data source.
 */
class CloudSqlConnectionFactoryKeyTest {

    private CloudSqlConnectionFactory factory;

    @BeforeEach
    void setUp() {
        factory = new CloudSqlConnectionFactory();
    }

    @Test
    void cacheKey_sameInputs_produceSameKey() {
        String a = factory.generateCacheKey("cloudsql-postgres", "proj:reg:inst", "app", "alice", "{sa}", "pw1");
        String b = factory.generateCacheKey("cloudsql-postgres", "proj:reg:inst", "app", "alice", "{sa}", "pw1");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void cacheKey_differentPassword_producesDifferentKey() {
        String a = factory.generateCacheKey("cloudsql-postgres", "proj:reg:inst", "app", "alice", "{sa}", "correct");
        String b = factory.generateCacheKey("cloudsql-postgres", "proj:reg:inst", "app", "alice", "{sa}", "wrong");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void cacheKey_differentServiceAccount_producesDifferentKey() {
        String a = factory.generateCacheKey("cloudsql-postgres", "proj:reg:inst", "app", "alice", "{sa-A}", "pw");
        String b = factory.generateCacheKey("cloudsql-postgres", "proj:reg:inst", "app", "alice", "{sa-B}", "pw");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void cacheKey_neverContainsPlaintextSecrets() {
        String key = factory.generateCacheKey("cloudsql-postgres", "proj:reg:inst", "app", "alice",
            "{\"private_key\":\"SECRET_SA\"}", "SECRET_PW");
        assertThat(key).doesNotContain("SECRET_SA").doesNotContain("SECRET_PW");
    }
}
