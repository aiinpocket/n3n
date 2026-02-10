package com.aiinpocket.n3n.backup.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CloudSyncStatus {
    private boolean enabled;
    private String provider;
    private String fingerprint;
    private String gatewayUrl;
}
