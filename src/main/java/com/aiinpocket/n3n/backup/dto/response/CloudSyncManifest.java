package com.aiinpocket.n3n.backup.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CloudSyncManifest {
    private String fingerprint;
    private int flowCount;
    private int credentialCount;
    private int aiProviderCount;
    private List<SyncEntityInfo> entities;
}
