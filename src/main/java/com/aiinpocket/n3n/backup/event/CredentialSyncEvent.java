package com.aiinpocket.n3n.backup.event;

import com.aiinpocket.n3n.credential.entity.Credential;

import java.util.UUID;

/**
 * Credential 同步事件
 */
public record CredentialSyncEvent(
        UUID entityId,
        SyncAction action,
        Credential entity
) implements CloudSyncEvent {

    @Override
    public String entityType() {
        return "credentials";
    }
}
