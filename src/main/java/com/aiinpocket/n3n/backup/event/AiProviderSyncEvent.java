package com.aiinpocket.n3n.backup.event;

import com.aiinpocket.n3n.ai.entity.AiProviderConfig;

import java.util.UUID;

/**
 * AI Provider 同步事件
 */
public record AiProviderSyncEvent(
        UUID entityId,
        SyncAction action,
        AiProviderConfig entity
) implements CloudSyncEvent {

    @Override
    public String entityType() {
        return "ai-providers";
    }
}
