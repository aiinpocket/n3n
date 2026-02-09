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
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
    private static final String STATE_HMAC_KEY;
    static {
        String envSecret = System.getenv("OAUTH2_STATE_SECRET");
        if (envSecret != null && !envSecret.isBlank()) {
            STATE_HMAC_KEY = envSecret;
        } else {
            // Generate a 256-bit random key (stronger than UUID's 122-bit entropy)
            byte[] keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
            STATE_HMAC_KEY = Base64.getEncoder().encodeToString(keyBytes);
        }
    }

    /**
     * Get authorization URL for a provider.
     * Requires authentication to ensure the credential belongs to the requesting user.
     */
    @GetMapping("/authorize/{provider}")
    public ResponseEntity<Map<String, String>> getAuthorizationUrl(
            @PathVariable @Size(max = 100) String provider,
            @RequestParam UUID credentialId,
            @RequestParam(required = false) @Size(max = 1000) String scope,
            @RequestParam(required = false) @Size(max = 2000) String redirectUri,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());

        // Verify credential ownership
        credentialRepository.findByIdAndOwnerId(credentialId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Credential not found or access denied"));

        String authUrl = buildAuthorizationUrl(provider, credentialId, scope, redirectUri);

        if (authUrl == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown OAuth2 provider"));
        }

        return ResponseEntity.ok(Map.of("authorizationUrl", authUrl));
    }

    /**
     * OAuth2 callback endpoint.
     * After processing the authorization code, redirects to the frontend callback page.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestParam(required = false) @Size(max = 2000) String code,
            @RequestParam(required = false) @Size(max = 2000) String state,
            @RequestParam(required = false) @Size(max = 500) String error,
            @RequestParam(required = false, name = "error_description") @Size(max = 2000) String errorDescription) {

        if (error != null) {
            log.error("OAuth2 error: {} - {}", error, errorDescription);
            return redirectToFrontend(null, error, errorDescription != null ? errorDescription : "Authorization failed");
        }

        if (code == null || state == null) {
            return redirectToFrontend(null, "invalid_request", "Missing code or state parameter");
        }

        // Parse state parameter (format: provider:credentialId:hmac)
        // Use limit=3 to avoid splitting HMAC value if it contains colons
        String[] stateParts = state.split(":", 3);
        if (stateParts.length < 3) {
            log.warn("OAuth2 callback with invalid state format (missing HMAC)");
            return redirectToFrontend(null, "invalid_state", "Invalid state parameter");
        }

        String provider = stateParts[0];
        UUID credentialId;
        try {
            credentialId = UUID.fromString(stateParts[1]);
        } catch (IllegalArgumentException e) {
            return redirectToFrontend(provider, "invalid_state", "Invalid credential ID in state");
        }

        // Verify HMAC signature to prevent state forgery (constant-time comparison)
        String expectedHmac = computeStateHmac(provider + ":" + credentialId);
        if (!java.security.MessageDigest.isEqual(
                expectedHmac.getBytes(StandardCharsets.UTF_8),
                stateParts[2].getBytes(StandardCharsets.UTF_8))) {
            log.warn("OAuth2 callback state HMAC mismatch for provider={}, credentialId={}", provider, credentialId);
            return redirectToFrontend(provider, "invalid_state", "Invalid state signature");
        }

        try {
            // Exchange code for token
            String tokenUrl = getTokenUrlForProvider(provider);
            if (tokenUrl == null) {
                return redirectToFrontend(provider, "unsupported_provider", "Unsupported OAuth2 provider");
            }

            // In production, client credentials should come from configuration
            String clientId = System.getenv(provider.toUpperCase() + "_CLIENT_ID");
            String clientSecret = System.getenv(provider.toUpperCase() + "_CLIENT_SECRET");
            String redirectUri = System.getenv("OAUTH2_REDIRECT_URI");

            if (clientId == null || clientSecret == null) {
                return redirectToFrontend(provider, "not_configured", "OAuth2 not configured for provider");
            }

            OAuth2Token token = tokenService.exchangeCode(
                credentialId, provider, code, tokenUrl,
                clientId, clientSecret, redirectUri
            );

            log.info("OAuth2 token obtained for provider {} and credential {}", provider, credentialId);

            return redirectToFrontend(provider, null, null);

        } catch (IOException e) {
            log.error("Failed to exchange OAuth2 code: {}", e.getClass().getSimpleName());
            return redirectToFrontend(provider, "token_exchange_failed", "Failed to exchange authorization code");
        }
    }

    /**
     * Redirect to frontend OAuth2 callback page with result parameters.
     */
    private ResponseEntity<Void> redirectToFrontend(String provider, String error, String errorDescription) {
        StringBuilder url = new StringBuilder("/oauth2/callback?");
        if (error != null) {
            url.append("error=").append(URLEncoder.encode(error, StandardCharsets.UTF_8));
            if (errorDescription != null) {
                url.append("&error_description=").append(URLEncoder.encode(errorDescription, StandardCharsets.UTF_8));
            }
        } else {
            url.append("success=true");
        }
        if (provider != null) {
            url.append("&provider=").append(URLEncoder.encode(provider, StandardCharsets.UTF_8));
        }
        return ResponseEntity.status(302)
                .header("Location", url.toString())
                .build();
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
