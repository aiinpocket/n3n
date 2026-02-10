package com.aiinpocket.n3n.backup.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class N3nCloudProviderTest {

    private MockWebServer mockServer;
    private N3nCloudProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TEST_FINGERPRINT = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
        String gatewayUrl = mockServer.url("").toString();
        if (gatewayUrl.endsWith("/")) {
            gatewayUrl = gatewayUrl.substring(0, gatewayUrl.length() - 1);
        }
        provider = new N3nCloudProvider(gatewayUrl, TEST_FINGERPRINT, objectMapper);
    }

    @AfterEach
    void tearDown() throws Exception {
        provider.close();
        mockServer.close();
    }

    @Test
    @Timeout(10)
    void upload_success() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"success\":true}"));

        byte[] data = "test-data".getBytes();
        provider.upload("flows/uuid1.json.enc", data);

        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/upload");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + TEST_FINGERPRINT);

        @SuppressWarnings("unchecked")
        Map<String, String> body = objectMapper.readValue(request.getBody().readUtf8(), Map.class);
        assertThat(body.get("filename")).isEqualTo("flows/uuid1.json.enc");
        assertThat(body.get("data")).isEqualTo(Base64.getEncoder().encodeToString(data));
    }

    @Test
    @Timeout(10)
    void upload_serverError_throwsIOException() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\":\"Internal server error\"}"));

        assertThatThrownBy(() -> provider.upload("test.enc", new byte[]{1}))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("500");
    }

    @Test
    @Timeout(10)
    void download_success() throws Exception {
        byte[] expectedData = "encrypted-content".getBytes();
        String responseJson = objectMapper.writeValueAsString(Map.of(
                "data", Base64.getEncoder().encodeToString(expectedData),
                "size", expectedData.length,
                "lastModified", "2026-02-10T12:00:00Z"
        ));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(responseJson));

        byte[] result = provider.download("flows/uuid1.json.enc");

        assertThat(result).isEqualTo(expectedData);

        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/download");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + TEST_FINGERPRINT);
    }

    @Test
    @Timeout(10)
    void download_notFound_throwsIOException() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"error\":\"File not found\"}"));

        assertThatThrownBy(() -> provider.download("missing.enc"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("404");
    }

    @Test
    @Timeout(10)
    void list_success() throws Exception {
        String responseJson = objectMapper.writeValueAsString(Map.of(
                "files", List.of(
                        Map.of("filename", "flows/uuid1.json.enc", "size", 100, "lastModified", "2026-02-10"),
                        Map.of("filename", "flows/uuid2.json.enc", "size", 200, "lastModified", "2026-02-09")
                )
        ));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(responseJson));

        List<CloudStorageProvider.StorageFileInfo> files = provider.list("flows/");

        assertThat(files).hasSize(2);
        assertThat(files.get(0).filename()).isEqualTo("flows/uuid1.json.enc");
        assertThat(files.get(0).size()).isEqualTo(100);
        assertThat(files.get(1).filename()).isEqualTo("flows/uuid2.json.enc");
    }

    @Test
    @Timeout(10)
    void list_emptyResult() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"files\":[]}"));

        List<CloudStorageProvider.StorageFileInfo> files = provider.list("");
        assertThat(files).isEmpty();
    }

    @Test
    @Timeout(10)
    void delete_success() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"success\":true,\"softDeleted\":true}"));

        provider.delete("flows/uuid1.json.enc");

        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/delete");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + TEST_FINGERPRINT);
    }

    @Test
    @Timeout(10)
    void testConnection_healthy_returnsTrue() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}"));

        assertThat(provider.testConnection()).isTrue();
    }

    @Test
    @Timeout(10)
    void testConnection_unhealthy_returnsFalse() {
        mockServer.enqueue(new MockResponse().setResponseCode(503));

        assertThat(provider.testConnection()).isFalse();
    }

    @Test
    void getProviderType_returnsDefault() {
        assertThat(provider.getProviderType()).isEqualTo("default");
    }
}
