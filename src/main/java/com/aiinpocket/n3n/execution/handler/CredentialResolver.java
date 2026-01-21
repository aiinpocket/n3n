package com.aiinpocket.n3n.execution.handler;

import java.util.Map;
import java.util.UUID;

/**
 * Interface for resolving encrypted credentials during node execution.
 */
public interface CredentialResolver {

    /**
     * Resolve and decrypt a credential by ID.
     *
     * @param credentialId the credential ID
     * @param userId the user requesting access (for permission checking)
     * @return decrypted credential data
     * @throws com.aiinpocket.n3n.common.exception.ResourceNotFoundException if credential not found
     * @throws org.springframework.security.access.AccessDeniedException if user has no access
     */
    Map<String, Object> resolve(UUID credentialId, UUID userId);

    /**
     * Check if a credential exists and user has access.
     *
     * @param credentialId the credential ID
     * @param userId the user to check access for
     * @return true if credential exists and user can access it
     */
    boolean canAccess(UUID credentialId, UUID userId);
}
