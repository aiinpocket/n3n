package com.aiinpocket.n3n.backup.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SyncEntityInfo {
    private String type;
    private String id;
    private String name;
    private String updatedAt;
}
