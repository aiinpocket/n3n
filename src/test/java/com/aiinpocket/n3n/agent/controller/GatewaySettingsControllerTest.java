package com.aiinpocket.n3n.agent.controller;

import com.aiinpocket.n3n.agent.entity.GatewaySettings;
import com.aiinpocket.n3n.agent.service.GatewaySettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewaySettingsControllerTest {

    @Mock
    private GatewaySettingsService gatewaySettingsService;

    @InjectMocks
    private GatewaySettingsController controller;

    private GatewaySettings sampleSettings() {
        return GatewaySettings.builder()
                .id(1L)
                .gatewayDomain("gateway.example.com")
                .gatewayPort(9443)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private GatewaySettings sampleSettings(String domain, int port, boolean enabled) {
        return GatewaySettings.builder()
                .id(1L)
                .gatewayDomain(domain)
                .gatewayPort(port)
                .enabled(enabled)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ===== getSettings (GET /api/settings/gateway) =====

    @Test
    void getSettings_success_returnsOk() {
        var settings = sampleSettings();
        when(gatewaySettingsService.getSettings()).thenReturn(settings);

        var result = controller.getSettings();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().domain()).isEqualTo("gateway.example.com");
        assertThat(result.getBody().port()).isEqualTo(9443);
        assertThat(result.getBody().enabled()).isTrue();
        verify(gatewaySettingsService).getSettings();
    }

    @Test
    void getSettings_responseContainsAllFields() {
        var settings = sampleSettings("test.example.com", 8443, false);
        when(gatewaySettingsService.getSettings()).thenReturn(settings);

        var result = controller.getSettings();

        var body = result.getBody();
        assertThat(body.domain()).isEqualTo("test.example.com");
        assertThat(body.port()).isEqualTo(8443);
        assertThat(body.enabled()).isFalse();
        assertThat(body.webSocketUrl()).isNotNull();
        assertThat(body.httpUrl()).isNotNull();
        assertThat(body.updatedAt()).isGreaterThan(0);
    }

    @Test
    void getSettings_defaultSettings_returnsLocalhostDefaults() {
        var settings = GatewaySettings.builder().build(); // defaults
        when(gatewaySettingsService.getSettings()).thenReturn(settings);

        var result = controller.getSettings();

        assertThat(result.getBody().domain()).isEqualTo("localhost");
        assertThat(result.getBody().port()).isEqualTo(9443);
        assertThat(result.getBody().enabled()).isTrue();
    }

    @Test
    void getSettings_withSecurePort_returnsWssUrl() {
        var settings = sampleSettings("secure.example.com", 9443, true);
        when(gatewaySettingsService.getSettings()).thenReturn(settings);

        var result = controller.getSettings();

        assertThat(result.getBody().webSocketUrl()).startsWith("wss://");
        assertThat(result.getBody().httpUrl()).startsWith("https://");
    }

    @Test
    void getSettings_withNonSecurePort_returnsWsUrl() {
        var settings = sampleSettings("dev.example.com", 8080, true);
        when(gatewaySettingsService.getSettings()).thenReturn(settings);

        var result = controller.getSettings();

        assertThat(result.getBody().webSocketUrl()).startsWith("ws://");
        assertThat(result.getBody().httpUrl()).startsWith("http://");
    }

    @Test
    void getSettings_serviceThrowsException_propagates() {
        when(gatewaySettingsService.getSettings()).thenThrow(new RuntimeException("Database error"));

        try {
            controller.getSettings();
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Database error");
        }
    }

    // ===== updateSettings (PUT /api/settings/gateway) =====

    @Test
    void updateSettings_allFields_returnsOk() {
        var request = new GatewaySettingsController.UpdateSettingsRequest("new.example.com", 443, true);
        var updatedSettings = sampleSettings("new.example.com", 443, true);
        when(gatewaySettingsService.updateSettings(eq("new.example.com"), eq(443), eq(true)))
                .thenReturn(updatedSettings);

        var result = controller.updateSettings(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("success", true);
        assertThat(body).containsKey("settings");
        assertThat(body).containsEntry("message", "Settings updated. Restart the server to apply changes.");
        verify(gatewaySettingsService).updateSettings("new.example.com", 443, true);
    }

    @Test
    void updateSettings_onlyDomain_delegatesToService() {
        var request = new GatewaySettingsController.UpdateSettingsRequest("only-domain.com", null, null);
        var updatedSettings = sampleSettings("only-domain.com", 9443, true);
        when(gatewaySettingsService.updateSettings(eq("only-domain.com"), isNull(), isNull()))
                .thenReturn(updatedSettings);

        var result = controller.updateSettings(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(gatewaySettingsService).updateSettings("only-domain.com", null, null);
    }

    @Test
    void updateSettings_onlyPort_delegatesToService() {
        var request = new GatewaySettingsController.UpdateSettingsRequest(null, 8443, null);
        var updatedSettings = sampleSettings("gateway.example.com", 8443, true);
        when(gatewaySettingsService.updateSettings(isNull(), eq(8443), isNull()))
                .thenReturn(updatedSettings);

        var result = controller.updateSettings(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(gatewaySettingsService).updateSettings(null, 8443, null);
    }

    @Test
    void updateSettings_onlyEnabled_delegatesToService() {
        var request = new GatewaySettingsController.UpdateSettingsRequest(null, null, false);
        var updatedSettings = sampleSettings("gateway.example.com", 9443, false);
        when(gatewaySettingsService.updateSettings(isNull(), isNull(), eq(false)))
                .thenReturn(updatedSettings);

        var result = controller.updateSettings(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(gatewaySettingsService).updateSettings(null, null, false);
    }

    @Test
    void updateSettings_serviceThrowsException_returnsBadRequest() {
        var request = new GatewaySettingsController.UpdateSettingsRequest("bad-domain", -1, null);
        when(gatewaySettingsService.updateSettings(anyString(), any(), any()))
                .thenThrow(new RuntimeException("Invalid settings"));

        var result = controller.updateSettings(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to update settings. Please try again.");
    }

    @Test
    void updateSettings_responseContainsSettings() {
        var request = new GatewaySettingsController.UpdateSettingsRequest("updated.com", 443, true);
        var updatedSettings = sampleSettings("updated.com", 443, true);
        when(gatewaySettingsService.updateSettings(anyString(), anyInt(), anyBoolean()))
                .thenReturn(updatedSettings);

        var result = controller.updateSettings(request);

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        var settingsResponse = (GatewaySettingsController.GatewaySettingsResponse) body.get("settings");
        assertThat(settingsResponse.domain()).isEqualTo("updated.com");
        assertThat(settingsResponse.port()).isEqualTo(443);
        assertThat(settingsResponse.enabled()).isTrue();
    }

    @Test
    void updateSettings_disableGateway_returnsOk() {
        var request = new GatewaySettingsController.UpdateSettingsRequest(null, null, false);
        var updatedSettings = sampleSettings("gateway.example.com", 9443, false);
        when(gatewaySettingsService.updateSettings(isNull(), isNull(), eq(false)))
                .thenReturn(updatedSettings);

        var result = controller.updateSettings(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        var settingsResponse = (GatewaySettingsController.GatewaySettingsResponse) body.get("settings");
        assertThat(settingsResponse.enabled()).isFalse();
    }

    @Test
    void updateSettings_enableGateway_returnsOk() {
        var request = new GatewaySettingsController.UpdateSettingsRequest(null, null, true);
        var updatedSettings = sampleSettings("gateway.example.com", 9443, true);
        when(gatewaySettingsService.updateSettings(isNull(), isNull(), eq(true)))
                .thenReturn(updatedSettings);

        var result = controller.updateSettings(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        var settingsResponse = (GatewaySettingsController.GatewaySettingsResponse) body.get("settings");
        assertThat(settingsResponse.enabled()).isTrue();
    }

    @Test
    void updateSettings_changeDomainAndPort_returnsUpdatedUrls() {
        var request = new GatewaySettingsController.UpdateSettingsRequest("prod.example.com", 443, true);
        var updatedSettings = sampleSettings("prod.example.com", 443, true);
        when(gatewaySettingsService.updateSettings(anyString(), anyInt(), anyBoolean()))
                .thenReturn(updatedSettings);

        var result = controller.updateSettings(request);

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        var settingsResponse = (GatewaySettingsController.GatewaySettingsResponse) body.get("settings");
        assertThat(settingsResponse.webSocketUrl()).contains("prod.example.com");
        assertThat(settingsResponse.httpUrl()).contains("prod.example.com");
    }

    // ===== DTO Tests =====

    @Test
    void gatewaySettingsResponse_mapsAllFields() {
        var settings = sampleSettings("test.com", 9443, true);
        var response = new GatewaySettingsController.GatewaySettingsResponse(
                settings.getGatewayDomain(),
                settings.getGatewayPort(),
                settings.getEnabled(),
                settings.getWebSocketUrl(),
                settings.getHttpUrl(),
                settings.getUpdatedAt().toEpochMilli()
        );

        assertThat(response.domain()).isEqualTo("test.com");
        assertThat(response.port()).isEqualTo(9443);
        assertThat(response.enabled()).isTrue();
        assertThat(response.webSocketUrl()).isNotNull();
        assertThat(response.httpUrl()).isNotNull();
        assertThat(response.updatedAt()).isGreaterThan(0);
    }

    @Test
    void updateSettingsRequest_allNullFields_accepted() {
        var request = new GatewaySettingsController.UpdateSettingsRequest(null, null, null);

        assertThat(request.domain()).isNull();
        assertThat(request.port()).isNull();
        assertThat(request.enabled()).isNull();
    }

    // ===== Cross-cutting concerns =====

    @Test
    void getSettings_returnsResponseEntity() {
        when(gatewaySettingsService.getSettings()).thenReturn(sampleSettings());

        var result = controller.getSettings();

        assertThat(result).isNotNull();
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateSettings_returnsResponseEntity() {
        var request = new GatewaySettingsController.UpdateSettingsRequest("test.com", 443, true);
        when(gatewaySettingsService.updateSettings(anyString(), anyInt(), anyBoolean()))
                .thenReturn(sampleSettings());

        var result = controller.updateSettings(request);

        assertThat(result).isNotNull();
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getSettings_port443_usesSecureProtocol() {
        var settings = sampleSettings("secure.example.com", 443, true);
        when(gatewaySettingsService.getSettings()).thenReturn(settings);

        var result = controller.getSettings();

        assertThat(result.getBody().webSocketUrl()).startsWith("wss://");
        assertThat(result.getBody().httpUrl()).startsWith("https://");
    }

    @Test
    void getSettings_port8080_usesInsecureProtocol() {
        var settings = sampleSettings("dev.example.com", 8080, true);
        when(gatewaySettingsService.getSettings()).thenReturn(settings);

        var result = controller.getSettings();

        assertThat(result.getBody().webSocketUrl()).startsWith("ws://");
        assertThat(result.getBody().httpUrl()).startsWith("http://");
    }

    @Test
    void updateSettings_emptyRequest_allFieldsNull() {
        var request = new GatewaySettingsController.UpdateSettingsRequest(null, null, null);
        var settings = sampleSettings();
        when(gatewaySettingsService.updateSettings(isNull(), isNull(), isNull()))
                .thenReturn(settings);

        var result = controller.updateSettings(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(gatewaySettingsService).updateSettings(null, null, null);
    }

    @Test
    void updateSettings_illegalArgumentException_returnsBadRequest() {
        var request = new GatewaySettingsController.UpdateSettingsRequest("bad", -1, null);
        when(gatewaySettingsService.updateSettings(anyString(), anyInt(), any()))
                .thenThrow(new IllegalArgumentException("Invalid port"));

        assertThat(controller.updateSettings(request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateSettings_nullPointerException_returnsBadRequest() {
        var request = new GatewaySettingsController.UpdateSettingsRequest("bad", -1, null);
        when(gatewaySettingsService.updateSettings(anyString(), anyInt(), any()))
                .thenThrow(new NullPointerException("Null value"));

        assertThat(controller.updateSettings(request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateSettings_genericRuntimeException_returnsBadRequest() {
        var request = new GatewaySettingsController.UpdateSettingsRequest("bad", -1, null);
        when(gatewaySettingsService.updateSettings(anyString(), anyInt(), any()))
                .thenThrow(new RuntimeException("Generic error"));

        assertThat(controller.updateSettings(request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
