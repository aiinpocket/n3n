package com.aiinpocket.n3n.base;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for repository tests using @SpringBootTest with transactional rollback.
 * Uses H2 in-memory database with PostgreSQL compatibility mode.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class BaseRepositoryTest {

    @PersistenceContext
    protected EntityManager entityManager;

    /**
     * Flush and clear the persistence context to ensure data is persisted.
     */
    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
