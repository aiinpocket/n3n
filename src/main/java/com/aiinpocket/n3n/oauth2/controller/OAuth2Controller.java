package com.aiinpocket.n3n.oauth2.controller;

import com.aiinpocket.n3n.oauth2.entity.OAuth2Token;
import com.aiinpocket.n3n.oauth2.service.OAuth2TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for OAuth2 authorization flows.
 */
@RestController
@RequestMapping("/api/oauth2")
@RequiredArgsConstructor
@Slf4j
public class OAuth2Controller {

    private final OAuth2TokenService tokenService;

    /**
     * Get authorization URL for a provider.
     */
    @GetMapping("/authorize/{provider}")
    public ResponseEntity<Map<String, String>> getAuthorizationUrl(
            @PathVariable String provider,
            @RequestParam UUID credentialId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String redirectUri) {

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

        // Parse state parameter (format: provider:credentialId)
        String[] stateParts = state.split(":");
        if (stateParts.length < 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid state parameter"));
        }

        String provider = stateParts[0];
        UUID credentialId;
        try {
            credentialId = UUID.fromString(stateParts[1]);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid credential ID in state"));
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
                "description", e.getMessage()
            ));
        }
    }

    /**
     * Check OAuth2 token status for a credential.
     */
    @GetMapping("/status/{credentialId}")
    public ResponseEntity<Map<String, Object>> getTokenStatus(@PathVariable UUID credentialId) {
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
     */
    @DeleteMapping("/disconnect/{credentialId}")
    public ResponseEntity<Map<String, Object>> disconnect(@PathVariable UUID credentialId) {
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

        String state = provider + ":" + credentialId.toString();
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
}
