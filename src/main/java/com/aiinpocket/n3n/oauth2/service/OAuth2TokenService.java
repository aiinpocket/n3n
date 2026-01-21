package com.aiinpocket.n3n.oauth2.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.oauth2.entity.OAuth2Token;
import com.aiinpocket.n3n.oauth2.repository.OAuth2TokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing OAuth2 tokens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2TokenService {

    private final OAuth2TokenRepository tokenRepository;
    private final ObjectMapper objectMapper;

    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    /**
     * Get a valid access token for a credential, refreshing if necessary.
     */
    @Transactional
    public String getAccessToken(UUID credentialId) {
        OAuth2Token token = tokenRepository.findByCredentialId(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth2 token not found for credential: " + credentialId));

        if (token.isExpiringSoon() && token.getRefreshToken() != null) {
            token = refreshToken(token);
        }

        return token.getAccessToken();
    }

    /**
     * Get the OAuth2 token entity for a credential.
     */
    public Optional<OAuth2Token> getToken(UUID credentialId) {
        return tokenRepository.findByCredentialId(credentialId);
    }

    /**
     * Store a new OAuth2 token.
     */
    @Transactional
    public OAuth2Token saveToken(UUID credentialId, String provider, String accessToken,
                                  String refreshToken, String tokenType, String scope,
                                  Integer expiresIn, String rawResponse) {
        // Delete existing token if any
        tokenRepository.deleteByCredentialId(credentialId);

        Instant expiresAt = expiresIn != null
            ? Instant.now().plusSeconds(expiresIn)
            : null;

        OAuth2Token token = OAuth2Token.builder()
            .credentialId(credentialId)
            .provider(provider)
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType(tokenType != null ? tokenType : "Bearer")
            .scope(scope)
            .expiresAt(expiresAt)
            .rawResponse(rawResponse)
            .build();

        return tokenRepository.save(token);
    }

    /**
     * Exchange authorization code for tokens.
     */
    @Transactional
    public OAuth2Token exchangeCode(UUID credentialId, String provider,
                                     String code, String tokenUrl,
                                     String clientId, String clientSecret,
                                     String redirectUri) throws IOException {
        RequestBody formBody = new FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("redirect_uri", redirectUri)
            .build();

        Request request = new Request.Builder()
            .url(tokenUrl)
            .post(formBody)
            .header("Accept", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("OAuth2 token exchange failed: {} - {}", response.code(), responseBody);
                throw new IOException("Token exchange failed: " + response.code());
            }

            JsonNode json = objectMapper.readTree(responseBody);

            String accessToken = json.path("access_token").asText(null);
            String refreshToken = json.path("refresh_token").asText(null);
            String tokenType = json.path("token_type").asText("Bearer");
            String scope = json.path("scope").asText(null);
            Integer expiresIn = json.has("expires_in") ? json.path("expires_in").asInt() : null;

            if (accessToken == null) {
                throw new IOException("No access_token in response");
            }

            return saveToken(credentialId, provider, accessToken, refreshToken,
                tokenType, scope, expiresIn, responseBody);
        }
    }

    /**
     * Refresh an expired token.
     */
    @Transactional
    public OAuth2Token refreshToken(OAuth2Token token) {
        String tokenUrl = getTokenUrlForProvider(token.getProvider());
        Map<String, String> clientCredentials = getClientCredentialsForProvider(token.getProvider());

        if (tokenUrl == null || clientCredentials == null) {
            log.warn("Cannot refresh token for provider: {}", token.getProvider());
            return token;
        }

        RequestBody formBody = new FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", token.getRefreshToken())
            .add("client_id", clientCredentials.get("clientId"))
            .add("client_secret", clientCredentials.get("clientSecret"))
            .build();

        Request request = new Request.Builder()
            .url(tokenUrl)
            .post(formBody)
            .header("Accept", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("OAuth2 token refresh failed: {} - {}", response.code(), responseBody);
                return token; // Return old token, let it fail naturally
            }

            JsonNode json = objectMapper.readTree(responseBody);

            String newAccessToken = json.path("access_token").asText(null);
            String newRefreshToken = json.path("refresh_token").asText(token.getRefreshToken());
            Integer expiresIn = json.has("expires_in") ? json.path("expires_in").asInt() : null;

            if (newAccessToken != null) {
                token.setAccessToken(newAccessToken);
                token.setRefreshToken(newRefreshToken);
                token.setExpiresAt(expiresIn != null ? Instant.now().plusSeconds(expiresIn) : null);
                token.setRawResponse(responseBody);
                return tokenRepository.save(token);
            }

            return token;
        } catch (IOException e) {
            log.error("Failed to refresh token: {}", e.getMessage());
            return token;
        }
    }

    /**
     * Delete OAuth2 token for a credential.
     */
    @Transactional
    public void deleteToken(UUID credentialId) {
        tokenRepository.deleteByCredentialId(credentialId);
    }

    /**
     * Get token URL for known providers.
     */
    private String getTokenUrlForProvider(String provider) {
        return switch (provider.toLowerCase()) {
            case "google" -> "https://oauth2.googleapis.com/token";
            case "github" -> "https://github.com/login/oauth/access_token";
            case "slack" -> "https://slack.com/api/oauth.v2.access";
            case "microsoft", "azure" -> "https://login.microsoftonline.com/common/oauth2/v2.0/token";
            default -> null;
        };
    }

    /**
     * Get client credentials for provider (should be from config in production).
     */
    private Map<String, String> getClientCredentialsForProvider(String provider) {
        // In production, these should come from configuration
        // For now, return null to indicate refresh is not supported without config
        return null;
    }
}
