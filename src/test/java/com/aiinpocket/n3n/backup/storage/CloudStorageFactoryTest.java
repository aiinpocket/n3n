package com.aiinpocket.n3n.backup.storage;

import com.aiinpocket.n3n.backup.entity.BackupSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudStorageFactoryTest {

    private CloudStorageFactory factory;

    @BeforeEach
    void setUp() {
        factory = new CloudStorageFactory(new ObjectMapper());
    }

    @Test
    void create_nullSettings_shouldThrow() {
        assertThatThrownBy(() -> factory.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void create_nullProvider_shouldThrow() {
        var settings = BackupSettings.builder().build();
        assertThatThrownBy(() -> factory.create(settings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void create_unsupportedProvider_shouldThrow() {
        var settings = BackupSettings.builder().provider("azure").build();
        assertThatThrownBy(() -> factory.create(settings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported backup provider");
    }

    @Test
    void create_s3Provider_shouldReturnS3Provider() {
        var settings = BackupSettings.builder()
                .provider("s3")
                .endpoint("https://s3.amazonaws.com")
                .region("us-east-1")
                .accessKey("test-key")
                .secretKey("test-secret")
                .bucket("test-bucket")
                .basePath("backups/")
                .build();

        try (CloudStorageProvider provider = factory.create(settings)) {
            assertThat(provider).isInstanceOf(S3StorageProvider.class);
            assertThat(provider.getProviderType()).isEqualTo("s3");
        }
    }

    @Test
    void create_r2Provider_shouldReturnS3ProviderWithR2Flag() {
        var settings = BackupSettings.builder()
                .provider("r2")
                .endpoint("https://abc123.r2.cloudflarestorage.com")
                .accessKey("test-key")
                .secretKey("test-secret")
                .bucket("test-bucket")
                .basePath("backups/")
                .build();

        try (CloudStorageProvider provider = factory.create(settings)) {
            assertThat(provider).isInstanceOf(S3StorageProvider.class);
            assertThat(provider.getProviderType()).isEqualTo("r2");
        }
    }

    @Test
    void create_gcsProvider_shouldReturnGcsProvider() {
        var settings = BackupSettings.builder()
                .provider("gcs")
                .serviceAccountJson("{\"type\":\"service_account\"}")
                .bucket("test-bucket")
                .basePath("backups/")
                .build();

        try (CloudStorageProvider provider = factory.create(settings)) {
            assertThat(provider).isInstanceOf(GcsStorageProvider.class);
            assertThat(provider.getProviderType()).isEqualTo("gcs");
        }
    }

    @Test
    void create_sftpProvider_shouldReturnSftpProvider() {
        var settings = BackupSettings.builder()
                .provider("sftp")
                .sftpHost("example.com")
                .sftpPort(22)
                .sftpUsername("user")
                .sftpPassword("pass")
                .sftpPath("/backups/")
                .build();

        try (CloudStorageProvider provider = factory.create(settings)) {
            assertThat(provider).isInstanceOf(SftpStorageProvider.class);
            assertThat(provider.getProviderType()).isEqualTo("sftp");
        }
    }

    @Test
    void create_s3WithLocalhostEndpoint_shouldThrowSsrf() {
        var settings = BackupSettings.builder()
                .provider("s3")
                .endpoint("https://localhost:9000")
                .accessKey("test")
                .secretKey("test")
                .bucket("test")
                .build();

        assertThatThrownBy(() -> factory.create(settings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("internal addresses");
    }

    @Test
    void create_sftpWithLoopback_shouldThrowSsrf() {
        var settings = BackupSettings.builder()
                .provider("sftp")
                .sftpHost("127.0.0.1")
                .sftpPort(22)
                .sftpUsername("user")
                .sftpPassword("pass")
                .build();

        assertThatThrownBy(() -> factory.create(settings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("internal addresses");
    }

    @Test
    void createDefault_shouldReturnN3nCloudProvider() {
        try (CloudStorageProvider provider = factory.createDefault(
                "https://us-central1-example.cloudfunctions.net/sync",
                "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd")) {
            assertThat(provider).isInstanceOf(N3nCloudProvider.class);
            assertThat(provider.getProviderType()).isEqualTo("default");
        }
    }

    @Test
    void createDefault_nullGatewayUrl_shouldThrow() {
        assertThatThrownBy(() -> factory.createDefault(null, "fingerprint"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Gateway URL");
    }

    @Test
    void createDefault_emptyGatewayUrl_shouldThrow() {
        assertThatThrownBy(() -> factory.createDefault("", "fingerprint"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Gateway URL");
    }

    @Test
    void createDefault_localhostGateway_shouldThrowSsrf() {
        assertThatThrownBy(() -> factory.createDefault(
                "https://localhost:8080/sync", "fingerprint"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("internal addresses");
    }

    @Test
    void create_r2WithNullRegion_shouldDefaultToAuto() {
        var settings = BackupSettings.builder()
                .provider("r2")
                .endpoint("https://abc123.r2.cloudflarestorage.com")
                .region(null)
                .accessKey("test-key")
                .secretKey("test-secret")
                .bucket("test-bucket")
                .build();

        try (CloudStorageProvider provider = factory.create(settings)) {
            assertThat(provider.getProviderType()).isEqualTo("r2");
        }
    }
}
