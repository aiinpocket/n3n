package com.aiinpocket.n3n.backup.event;

import java.util.UUID;

/**
 * 雲端同步事件基底介面
 */
public sealed interface CloudSyncEvent
        permits FlowSyncEvent, CredentialSyncEvent, AiProviderSyncEvent {

    String entityType();

    UUID entityId();

    SyncAction action();
}
