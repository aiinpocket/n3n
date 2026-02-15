package com.aiinpocket.n3n.oauth2.controller;

import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.oauth2.entity.OAuth2Token;
import com.aiinpocket.n3n.oauth2.service.OAuth2TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2ControllerTest {

    @Mock
    private OAuth2TokenService tokenService;

    @Mock
    private CredentialRepository credentialRepository;

    @InjectMocks
    private OAuth2Controller oAuth2Controller;

    private final UUID userId = UUID.randomUUID();

    private UserDetails testUser() {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private Credential sampleCredential(UUID credentialId) {
        var cred = new Credential();
        cred.setId(credentialId);
        cred.setName("Test Credential");
        cred.setType("oauth2");
        cred.setOwnerId(userId);
        return cred;
    }

    private OAuth2Token sampleToken(UUID credentialId) {
        return OAuth2Token.builder()
                .id(UUID.randomUUID())
                .credentialId(credentialId)
                .provider("google")
                .accessToken("access-token-123")
                .refreshToken("refresh-token-123")
                .tokenType("Bearer")
                .scope("email profile")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    // ========== getAuthorizationUrl ==========

    @Test
    void getAuthorizationUrl_credentialNotFound_throwsException() {
        var credentialId = UUID.randomUUID();
        when(credentialRepository.findByIdAndOwnerId(credentialId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> oAuth2Controller.getAuthorizationUrl(
                "google", credentialId, null, null, testUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Credential not found or access denied");
    }

    @Test
    void getAuthorizationUrl_credentialNotOwned_throwsException() {
        var credentialId = UUID.randomUUID();
        when(credentialRepository.findByIdAndOwnerId(credentialId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> oAuth2Controller.getAuthorizationUrl(
                "google", credentialId, null, null, testUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Credential not found or access denied");
    }

    @Test
    void getAuthorizationUrl_unsupportedProvider_returnsBadRequest() {
        var credentialId = UUID.randomUUID();
        when(credentialRepository.findByIdAndOwnerId(credentialId, userId))
                .thenReturn(Optional.of(sampleCredential(credentialId)));

        // No env vars set, so buildAuthorizationUrl returns null for any provider
        var result = oAuth2Controller.getAuthorizationUrl(
                "unsupported", credentialId, null, null, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("error")).isEqualTo("Unknown OAuth2 provider");
    }

    @Test
    void getAuthorizationUrl_noClientIdConfigured_returnsBadRequest() {
        var credentialId = UUID.randomUUID();
        when(credentialRepository.findByIdAndOwnerId(credentialId, userId))
                .thenReturn(Optional.of(sampleCredential(credentialId)));

        // Without env vars GOOGLE_CLIENT_ID and OAUTH2_REDIRECT_URI, result will be null
        var result = oAuth2Controller.getAuthorizationUrl(
                "google", credentialId, null, null, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).containsKey("error");
    }

    // ========== handleCallback ==========

    @Test
    void handleCallback_withError_redirectsWithError() {
        var result = oAuth2Controller.handleCallback(null, null, "access_denied", "User denied access");

        assertThat(result.getStatusCode().value()).isEqualTo(302);
        var location = result.getHeaders().getFirst("Location");
        assertThat(location).contains("error=access_denied");
        assertThat(location).contains("error_description=User+denied+access");
    }

    @Test
    void handleCallback_withErrorNoDescription_redirectsWithDefaultDescription() {
        var result = oAuth2Controller.handleCallback(null, null, "server_error", null);

        assertThat(result.getStatusCode().value()).isEqualTo(302);
        var location = result.getHeaders().getFirst("Location");
        assertThat(location).contains("error=server_error");
        assertThat(location).contains("error_description=Authorization+failed");
    }

    @Test
    void handleCallback_missingCode_redirectsWithError() {
        var result = oAuth2Controller.handleCallback(null, "some-state", null, null);

        assertThat(result.getStatusCode().value()).isEqualTo(302);
        var location = result.getHeaders().getFirst("Location");
        assertThat(location).contains("error=invalid_request");
        assertThat(location).contains("Missing+code+or+state");
    }

    @Test
    void handleCallback_missingState_redirectsWithError() {
        var result = oAuth2Controller.handleCallback("auth-code", null, null, null);

        assertThat(result.getStatusCode().value()).isEqualTo(302);
        var location = result.getHeaders().getFirst("Location");
        assertThat(location).contains("error=invalid_request");
        assertThat(location).contains("Missing+code+or+state");
    }

    @Test
    void handleCallback_invalidStateFormat_redirectsWithError() {
        // State with only one part (missing HMAC)
        var result = oAuth2Controller.handleCallback("auth-code", "google", null, null);

        assertThat(result.getStatusCode().value()).isEqualTo(302);
        var location = result.getHeaders().getFirst("Location");
        assertThat(location).contains("error=invalid_state");
        assertThat(location).contains("Invalid+state+parameter");
    }

    @Test
    void handleCallback_invalidCredentialIdInState_redirectsWithError() {
        var result = oAuth2Controller.handleCallback("auth-code", "google:invalid-uuid:hmac", null, null);

        assertThat(result.getStatusCode().value()).isEqualTo(302);
        var location = result.getHeaders().getFirst("Location");
        assertThat(location).contains("error=invalid_state");
        assertThat(location).contains("provider=google");
    }

    @Test
    void handleCallback_invalidHmac_redirectsWithError() {
        var credentialId = UUID.randomUUID();
        var state = "google:" + credentialId + ":invalid-hmac";

        var result = oAuth2Controller.handleCallback("auth-code", state, null, null);

        assertThat(result.getStatusCode().value()).isEqualTo(302);
        var location = result.getHeaders().getFirst("Location");
        assertThat(location).contains("error=invalid_state");
        assertThat(location).contains("Invalid+state+signature");
    }

    // ========== getTokenStatus ==========

    @Test
    void getTokenStatus_connected_returnsTokenInfo() {
        var credentialId = UUID.randomUUID();
        var token = sampleToken(credentialId);
        when(credentialRepository.findByIdAndOwnerId(credentialId, userId))
                .thenReturn(Optional.of(sampleCredential(credentialId)));
        when(tokenService.getToken(credentialId)).thenReturn(Optional.of(token));

        var result = oAuth2Controller.getTokenStatus(credentialId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("connected")).isEqualTo(true);
        assertThat(result.getBody().get("provider")).isEqualTo("google");
        assertThat(result.getBody().get("expired")).isEqualTo(false);
    }

    @Test
    void getTokenStatus_notConnected_returnsDisconnected() {
        var credentialId = UUID.randomUUID();
        when(credentialRepository.findByIdAndOwnerId(credentialId, userId))
                .thenReturn(Optional.of(sampleCredential(credentialId)));
        when(tokenService.getToken(credentialId)).thenReturn(Optional.empty());

        var result = oAuth2Controller.getTokenStatus(credentialId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("connected")).isEqualTo(false);
    }

    @Test
    void getTokenStatus_credentialNotFound_throwsException() {
        var credentialId = UUID.randomUUID();
        when(credentialRepository.findByIdAndOwnerId(credentialId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> oAuth2Controller.getTokenStatus(credentialId, testUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Credential not found or access denied");
    }

    @Test
    void getTokenStatus_expiredToken_returnsExpired() {
        var credentialId = UUID.randomUUID();
        var token = OAuth2Token.builder()
                .id(UUID.randomUUID())
                .credentialId(credentialId)
                .provider("github")
                .accessToken("expired-token")
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();
        when(credentialRepository.findByIdAndOwnerId(credentialId, userId))
                .thenReturn(Optional.of(sampleCredential(credentialId)));
        when(tokenService.getToken(credentialId)).thenReturn(Optional.of(token));

        var result = oAuth2Controller.getTokenStatus(credentialId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("connected")).isEqualTo(true);
        assertThat(result.getBody().get("expired")).isEqualTo(true);
    }

    @Test
    void getTokenStatus_tokenWithNoExpiry_returnsNeverExpires() {
        var credentialId = UUID.randomUUID();
        var token = OAuth2Token.builder()
                .id(UUID.randomUUID())
                .credentialId(credentialId)
                .provider("slack")
                .accessToken("no-expiry-token")
                .expiresAt(null)
                .scope(null)
                .build();
        when(credentialRepository.findByIdAndOwnerId(credentialId, userId))
                .thenReturn(Optional.of(sampleCredential(credentialId)));
        when(tokenService.getToken(credentialId)).thenReturn(Optional.of(token));

        var result = oAuth2Controller.getTokenStatus(credentialId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("expired")).isEqualTo(false);
        assertThat(result.getBody().get("expiresAt")).isEqualTo("never");
        assertThat(result.getBody().get("scope")).isEqualTo("");
    }

    // ========== disconnect ==========

    @Test
    void disconnect_success_returnsOk() {
        var credentialId = UUID.randomUUID();
        when(credentialRepository.findByIdAndOwnerId(credentialId, userId))
                .thenReturn(Optional.of(sampleCredential(credentialId)));

        var result = oAuth2Controller.disconnect(credentialId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("success")).isEqualTo(true);
        verify(tokenService).deleteToken(credentialId);
    }

    @Test
    void disconnect_credentialNotFound_throwsException() {
        var credentialId = UUID.randomUUID();
        when(credentialRepository.findByIdAndOwnerId(credentialId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> oAuth2Controller.disconnect(credentialId, testUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Credential not found or access denied");

        verify(tokenService, never()).deleteToken(any());
    }

    @Test
    void disconnect_credentialNotOwned_throwsException() {
        var credentialId = UUID.randomUUID();
        when(credentialRepository.findByIdAndOwnerId(credentialId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> oAuth2Controller.disconnect(credentialId, testUser()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(tokenService, never()).deleteToken(any());
    }
}
