package com.aiinpocket.n3n.oauth2.controller;

import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.oauth2.entity.OAuth2Token;
import com.aiinpocket.n3n.oauth2.service.OAuth2TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for OAuth2 authorization flows.
 */
@RestController
@RequestMapping("/api/oauth2")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "OAuth2", description = "OAuth2 integration")
public class OAuth2Controller {

    private final OAuth2TokenService tokenService;
    private final CredentialRepository credentialRepository;

    // HMAC key for signing OAuth2 state parameter to prevent CSRF/forgery
    private static final String STATE_HMAC_KEY = System.getenv("OAUTH2_STATE_SECRET") != null
        ? System.getenv("OAUTH2_STATE_SECRET")
        : UUID.randomUUID().toString();

    /**
     * Get authorization URL for a provider.
     * Requires authentication to ensure the credential belongs to the requesting user.
     */
    @GetMapping("/authorize/{provider}")
    public ResponseEntity<Map<String, String>> getAuthorizationUrl(
            @PathVariable String provider,
            @RequestParam UUID credentialId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String redirectUri,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());

        // Verify credential ownership
        credentialRepository.findByIdAndOwnerId(credentialId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Credential not found or access denied"));

        String authUrl = buildAuthorizationUrl(provider, credentialId, scope, redirectUri);

        if (authUrl == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown provider: " + provider));
        }

        return ResponseEntity.ok(Map.of("authorizationUrl", authUrl));
    }

    /**
     * OAuth2 callback endpoint.
     */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(
            @RequestParam String code,
            @RequestParam String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false, name = "error_description") String errorDescription) {

        if (error != null) {
            log.error("OAuth2 error: {} - {}", error, errorDescription);
            return ResponseEntity.badRequest().body(Map.of(
                "error", error,
                "description", errorDescription != null ? errorDescription : "Authorization failed"
            ));
        }

        // Parse state parameter (format: provider:credentialId:hmac)
        String[] stateParts = state.split(":");
        if (stateParts.length < 3) {
            log.warn("OAuth2 callback with invalid state format (missing HMAC)");
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid state parameter"));
        }

        String provider = stateParts[0];
        UUID credentialId;
        try {
            credentialId = UUID.fromString(stateParts[1]);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid credential ID in state"));
        }

        // Verify HMAC signature to prevent state forgery
        String expectedHmac = computeStateHmac(provider + ":" + credentialId);
        if (!expectedHmac.equals(stateParts[2])) {
            log.warn("OAuth2 callback state HMAC mismatch for provider={}, credentialId={}", provider, credentialId);
            return ResponseEntity.status(403).body(Map.of("error", "Invalid state signature"));
        }

        try {
            // Exchange code for token
            String tokenUrl = getTokenUrlForProvider(provider);
            // In production, client credentials should come from configuration
            String clientId = System.getenv(provider.toUpperCase() + "_CLIENT_ID");
            String clientSecret = System.getenv(provider.toUpperCase() + "_CLIENT_SECRET");
            String redirectUri = System.getenv("OAUTH2_REDIRECT_URI");

            if (clientId == null || clientSecret == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "OAuth2 not configured for provider: " + provider
                ));
            }

            OAuth2Token token = tokenService.exchangeCode(
                credentialId, provider, code, tokenUrl,
                clientId, clientSecret, redirectUri
            );

            log.info("OAuth2 token obtained for provider {} and credential {}", provider, credentialId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "provider", provider,
                "credentialId", credentialId.toString(),
                "expiresAt", token.getExpiresAt() != null ? token.getExpiresAt().toString() : "never"
            ));

        } catch (IOException e) {
            log.error("Failed to exchange OAuth2 code: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "token_exchange_failed",
                "description", "Failed to exchange authorization code. Please try again."
            ));
        }
    }

    /**
     * Check OAuth2 token status for a credential.
     * Requires authentication and credential ownership.
     */
    @GetMapping("/status/{credentialId}")
    public ResponseEntity<Map<String, Object>> getTokenStatus(
            @PathVariable UUID credentialId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        // Verify credential ownership
        credentialRepository.findByIdAndOwnerId(credentialId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Credential not found or access denied"));

        return tokenService.getToken(credentialId)
            .map(token -> {
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("connected", true);
                result.put("provider", token.getProvider());
                result.put("expired", token.isExpired());
                result.put("expiringSoon", token.isExpiringSoon());
                result.put("expiresAt", token.getExpiresAt() != null ? token.getExpiresAt().toString() : "never");
                result.put("scope", token.getScope() != null ? token.getScope() : "");
                return ResponseEntity.ok(result);
            })
            .orElseGet(() -> {
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("connected", false);
                return ResponseEntity.ok(result);
            });
    }

    /**
     * Disconnect OAuth2 for a credential.
     * Requires authentication and credential ownership.
     */
    @DeleteMapping("/disconnect/{credentialId}")
    public ResponseEntity<Map<String, Object>> disconnect(
            @PathVariable UUID credentialId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        // Verify credential ownership
        credentialRepository.findByIdAndOwnerId(credentialId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Credential not found or access denied"));

        tokenService.deleteToken(credentialId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private String buildAuthorizationUrl(String provider, UUID credentialId, String scope, String redirectUri) {
        String clientId = System.getenv(provider.toUpperCase() + "_CLIENT_ID");
        if (redirectUri == null) {
            redirectUri = System.getenv("OAUTH2_REDIRECT_URI");
        }

        if (clientId == null || redirectUri == null) {
            return null;
        }

        String statePayload = provider + ":" + credentialId.toString();
        String hmac = computeStateHmac(statePayload);
        String state = statePayload + ":" + hmac;
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8);

        return switch (provider.toLowerCase()) {
            case "google" -> {
                String googleScope = scope != null ? scope : "https://www.googleapis.com/auth/gmail.send https://www.googleapis.com/auth/spreadsheets";
                yield "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?client_id=" + clientId +
                    "&redirect_uri=" + encodedRedirect +
                    "&response_type=code" +
                    "&scope=" + URLEncoder.encode(googleScope, StandardCharsets.UTF_8) +
                    "&access_type=offline" +
                    "&prompt=consent" +
                    "&state=" + encodedState;
            }
            case "github" -> {
                String githubScope = scope != null ? scope : "repo user";
                yield "https://github.com/login/oauth/authorize" +
                    "?client_id=" + clientId +
                    "&redirect_uri=" + encodedRedirect +
                    "&scope=" + URLEncoder.encode(githubScope, StandardCharsets.UTF_8) +
                    "&state=" + encodedState;
            }
            case "slack" -> {
                String slackScope = scope != null ? scope : "chat:write channels:read";
                yield "https://slack.com/oauth/v2/authorize" +
                    "?client_id=" + clientId +
                    "&redirect_uri=" + encodedRedirect +
                    "&scope=" + URLEncoder.encode(slackScope, StandardCharsets.UTF_8) +
                    "&state=" + encodedState;
            }
            case "microsoft", "azure" -> {
                String msScope = scope != null ? scope : "https://graph.microsoft.com/.default offline_access";
                yield "https://login.microsoftonline.com/common/oauth2/v2.0/authorize" +
                    "?client_id=" + clientId +
                    "&redirect_uri=" + encodedRedirect +
                    "&response_type=code" +
                    "&scope=" + URLEncoder.encode(msScope, StandardCharsets.UTF_8) +
                    "&state=" + encodedState;
            }
            default -> null;
        };
    }

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
     * Compute HMAC-SHA256 signature for OAuth2 state parameter to prevent CSRF/forgery.
     */
    private static String computeStateHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(STATE_HMAC_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC for OAuth2 state", e);
        }
    }
}
