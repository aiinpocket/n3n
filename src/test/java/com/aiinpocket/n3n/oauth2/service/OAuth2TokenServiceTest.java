package com.aiinpocket.n3n.oauth2.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.oauth2.entity.OAuth2Token;
import com.aiinpocket.n3n.oauth2.repository.OAuth2TokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OAuth2TokenServiceTest extends BaseServiceTest {

    @Mock
    private OAuth2TokenRepository tokenRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OAuth2TokenService tokenService;

    private UUID credentialId;

    @BeforeEach
    void setUp() {
        credentialId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Get Access Token")
    class GetAccessToken {

        @Test
        void getAccessToken_validToken_returnsAccessToken() {
            OAuth2Token token = OAuth2Token.builder()
                .credentialId(credentialId)
                .provider("google")
                .accessToken("valid-access-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

            when(tokenRepository.findByCredentialId(credentialId)).thenReturn(Optional.of(token));

            String result = tokenService.getAccessToken(credentialId);

            assertThat(result).isEqualTo("valid-access-token");
        }

        @Test
        void getAccessToken_noToken_throwsException() {
            when(tokenRepository.findByCredentialId(credentialId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tokenService.getAccessToken(credentialId))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getAccessToken_tokenNoExpiry_returnsAccessToken() {
            OAuth2Token token = OAuth2Token.builder()
                .credentialId(credentialId)
                .provider("github")
                .accessToken("no-expiry-token")
                .expiresAt(null)
                .build();

            when(tokenRepository.findByCredentialId(credentialId)).thenReturn(Optional.of(token));

            String result = tokenService.getAccessToken(credentialId);

            assertThat(result).isEqualTo("no-expiry-token");
        }
    }

    @Nested
    @DisplayName("Get Token")
    class GetToken {

        @Test
        void getToken_existing_returnsOptionalWithToken() {
            OAuth2Token token = OAuth2Token.builder()
                .credentialId(credentialId)
                .provider("google")
                .accessToken("token")
                .build();

            when(tokenRepository.findByCredentialId(credentialId)).thenReturn(Optional.of(token));

            Optional<OAuth2Token> result = tokenService.getToken(credentialId);

            assertThat(result).isPresent();
            assertThat(result.get().getProvider()).isEqualTo("google");
        }

        @Test
        void getToken_nonExisting_returnsEmpty() {
            when(tokenRepository.findByCredentialId(credentialId)).thenReturn(Optional.empty());

            Optional<OAuth2Token> result = tokenService.getToken(credentialId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Save Token")
    class SaveToken {

        @Test
        void saveToken_withExpiresIn_calculatesExpiry() {
            when(tokenRepository.save(any(OAuth2Token.class))).thenAnswer(inv -> {
                OAuth2Token t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });

            OAuth2Token result = tokenService.saveToken(
                credentialId, "google", "access", "refresh",
                "Bearer", "openid email", 3600, "{}"
            );

            assertThat(result.getCredentialId()).isEqualTo(credentialId);
            assertThat(result.getProvider()).isEqualTo("google");
            assertThat(result.getAccessToken()).isEqualTo("access");
            assertThat(result.getRefreshToken()).isEqualTo("refresh");
            assertThat(result.getTokenType()).isEqualTo("Bearer");
            assertThat(result.getExpiresAt()).isNotNull();
            assertThat(result.getExpiresAt()).isAfter(Instant.now());
            verify(tokenRepository).deleteByCredentialId(credentialId);
        }

        @Test
        void saveToken_nullExpiresIn_noExpiry() {
            when(tokenRepository.save(any(OAuth2Token.class))).thenAnswer(inv -> inv.getArgument(0));

            OAuth2Token result = tokenService.saveToken(
                credentialId, "github", "access", null,
                null, null, null, "{}"
            );

            assertThat(result.getExpiresAt()).isNull();
            assertThat(result.getTokenType()).isEqualTo("Bearer");
        }

        @Test
        void saveToken_deletesExistingTokenFirst() {
            when(tokenRepository.save(any(OAuth2Token.class))).thenAnswer(inv -> inv.getArgument(0));

            tokenService.saveToken(credentialId, "google", "access", null, null, null, null, null);

            verify(tokenRepository).deleteByCredentialId(credentialId);
            verify(tokenRepository).save(any(OAuth2Token.class));
        }
    }

    @Nested
    @DisplayName("Delete Token")
    class DeleteToken {

        @Test
        void deleteToken_deletesFromRepository() {
            tokenService.deleteToken(credentialId);

            verify(tokenRepository).deleteByCredentialId(credentialId);
        }
    }

    @Nested
    @DisplayName("OAuth2Token Entity Methods")
    class OAuth2TokenEntityMethods {

        @Test
        void isExpired_tokenNotExpired_returnsFalse() {
            OAuth2Token token = OAuth2Token.builder()
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

            assertThat(token.isExpired()).isFalse();
        }

        @Test
        void isExpired_tokenExpired_returnsTrue() {
            OAuth2Token token = OAuth2Token.builder()
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();

            assertThat(token.isExpired()).isTrue();
        }

        @Test
        void isExpired_noExpiry_returnsFalse() {
            OAuth2Token token = OAuth2Token.builder()
                .expiresAt(null)
                .build();

            assertThat(token.isExpired()).isFalse();
        }

        @Test
        void isExpiringSoon_withinFiveMinutes_returnsTrue() {
            OAuth2Token token = OAuth2Token.builder()
                .expiresAt(Instant.now().plusSeconds(120))
                .build();

            assertThat(token.isExpiringSoon()).isTrue();
        }

        @Test
        void isExpiringSoon_notExpiringSoon_returnsFalse() {
            OAuth2Token token = OAuth2Token.builder()
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

            assertThat(token.isExpiringSoon()).isFalse();
        }

        @Test
        void isExpiringSoon_noExpiry_returnsFalse() {
            OAuth2Token token = OAuth2Token.builder()
                .expiresAt(null)
                .build();

            assertThat(token.isExpiringSoon()).isFalse();
        }

        @Test
        void isExpired_exactlyNow_returnsTrue() {
            OAuth2Token token = OAuth2Token.builder()
                .expiresAt(Instant.now().minusMillis(1))
                .build();

            assertThat(token.isExpired()).isTrue();
        }

        @Test
        void isExpiringSoon_alreadyExpired_returnsTrue() {
            OAuth2Token token = OAuth2Token.builder()
                .expiresAt(Instant.now().minusSeconds(60))
                .build();

            assertThat(token.isExpiringSoon()).isTrue();
        }
    }

    @Nested
    @DisplayName("Get Access Token With Refresh")
    class GetAccessTokenWithRefresh {

        @Test
        void getAccessToken_expiringSoonWithRefreshToken_attemptsRefresh() {
            // Given - token expiring within 5 minutes, has refresh token
            // Since refreshToken calls external HTTP, the token refresh will fail
            // and return the original token (as designed)
            OAuth2Token token = OAuth2Token.builder()
                .credentialId(credentialId)
                .provider("custom") // unknown provider so refresh returns original token
                .accessToken("almost-expired-token")
                .refreshToken("refresh-token")
                .expiresAt(Instant.now().plusSeconds(60)) // less than 5 min
                .build();

            when(tokenRepository.findByCredentialId(credentialId)).thenReturn(Optional.of(token));

            // When
            String result = tokenService.getAccessToken(credentialId);

            // Then - should return original token since refresh fails for unknown provider
            assertThat(result).isEqualTo("almost-expired-token");
        }

        @Test
        void getAccessToken_expiringSoonNoRefreshToken_returnsCurrentToken() {
            // Given - token expiring soon, but no refresh token
            OAuth2Token token = OAuth2Token.builder()
                .credentialId(credentialId)
                .provider("google")
                .accessToken("expiring-token")
                .refreshToken(null)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

            when(tokenRepository.findByCredentialId(credentialId)).thenReturn(Optional.of(token));

            // When
            String result = tokenService.getAccessToken(credentialId);

            // Then
            assertThat(result).isEqualTo("expiring-token");
        }
    }

    @Nested
    @DisplayName("Save Token - Edge Cases")
    class SaveTokenEdgeCases {

        @Test
        void saveToken_withAllFields_setsAllProperties() {
            when(tokenRepository.save(any(OAuth2Token.class))).thenAnswer(inv -> {
                OAuth2Token t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });

            OAuth2Token result = tokenService.saveToken(
                credentialId, "slack", "xoxb-token", "xoxr-refresh",
                "token", "chat:write", 7200, "{\"ok\":true}"
            );

            assertThat(result.getProvider()).isEqualTo("slack");
            assertThat(result.getScope()).isEqualTo("chat:write");
            assertThat(result.getRawResponse()).isEqualTo("{\"ok\":true}");
            assertThat(result.getTokenType()).isEqualTo("token");
        }

        @Test
        void saveToken_zeroExpiresIn_setsExpiryCloseToNow() {
            when(tokenRepository.save(any(OAuth2Token.class))).thenAnswer(inv -> inv.getArgument(0));

            OAuth2Token result = tokenService.saveToken(
                credentialId, "google", "access", null,
                null, null, 0, null
            );

            // expiresAt should be very close to now (0 seconds in the future)
            assertThat(result.getExpiresAt()).isNotNull();
            assertThat(result.getExpiresAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(1));
        }
    }
}
