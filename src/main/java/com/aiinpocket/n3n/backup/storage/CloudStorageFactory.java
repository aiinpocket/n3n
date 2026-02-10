package com.aiinpocket.n3n.backup.storage;

import com.aiinpocket.n3n.backup.entity.BackupSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

/**
 * 雲端儲存提供者工廠
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CloudStorageFactory {

    private final ObjectMapper objectMapper;

    /**
     * 根據設定建立對應的儲存提供者
     */
    public CloudStorageProvider create(BackupSettings settings) {
        if (settings == null || settings.getProvider() == null) {
            throw new IllegalArgumentException("Backup provider not configured");
        }

        return switch (settings.getProvider().toLowerCase()) {
            case "s3" -> {
                validateEndpoint(settings.getEndpoint());
                yield new S3StorageProvider(
                        settings.getEndpoint(),
                        settings.getRegion(),
                        settings.getAccessKey(),
                        settings.getSecretKey(),
                        settings.getBucket(),
                        settings.getBasePath(),
                        false
                );
            }
            case "r2" -> {
                validateEndpoint(settings.getEndpoint());
                yield new S3StorageProvider(
                        settings.getEndpoint(),
                        settings.getRegion() != null ? settings.getRegion() : "auto",
                        settings.getAccessKey(),
                        settings.getSecretKey(),
                        settings.getBucket(),
                        settings.getBasePath(),
                        true
                );
            }
            case "gcs" -> new GcsStorageProvider(
                    settings.getServiceAccountJson(),
                    settings.getBucket(),
                    settings.getBasePath(),
                    objectMapper
            );
            case "sftp" -> {
                validateHost(settings.getSftpHost());
                yield new SftpStorageProvider(
                        settings.getSftpHost(),
                        settings.getSftpPort() != null ? settings.getSftpPort() : 22,
                        settings.getSftpUsername(),
                        settings.getSftpPassword(),
                        settings.getSftpPrivateKey(),
                        settings.getSftpPath()
                );
            }
            default -> throw new IllegalArgumentException("Unsupported backup provider: " + settings.getProvider());
        };
    }

    /**
     * SSRF 防護：驗證端點 URL 不指向內部地址
     */
    private void validateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return;
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid endpoint URL: " + endpoint);
        }
        String host = uri.getHost();
        if (host != null) {
            validateHost(host);
        }
    }

    /**
     * SSRF 防護：驗證主機名不指向內部地址
     */
    private void validateHost(String host) {
        if (host == null || host.isBlank()) return;
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
                throw new IllegalArgumentException(
                        "Backup endpoint must not point to internal addresses: " + host);
            }
        } catch (java.net.UnknownHostException e) {
            log.warn("Cannot resolve backup endpoint host: {}", host);
        }
    }
}
