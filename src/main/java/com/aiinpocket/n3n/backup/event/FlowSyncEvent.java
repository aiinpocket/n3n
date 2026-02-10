package com.aiinpocket.n3n.backup.event;

import com.aiinpocket.n3n.flow.entity.Flow;

import java.util.UUID;

/**
 * Flow 同步事件（包含 CRUD 操作）
 */
public record FlowSyncEvent(
        UUID entityId,
        SyncAction action,
        Flow entity
) implements CloudSyncEvent {

    @Override
    public String entityType() {
        return "flows";
    }
}
