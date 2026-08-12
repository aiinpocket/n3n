package com.aiinpocket.n3n.auth.service;

import com.aiinpocket.n3n.auth.exception.BadCredentialsException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * 呼叫 Google tokeninfo 端點驗證 Google ID Token。
 * tokeninfo 會驗證簽章與有效期限（過期 token 回傳非 2xx），
 * aud / email_verified 等業務驗證由 {@link GoogleAuthService} 負責。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleTokenVerifier {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * Claims extracted from a Google ID token.
     */
    public record GoogleTokenInfo(
        String aud,
        String email,
        boolean emailVerified,
        String name,
        String picture,
        String sub
    ) {}

    /**
     * Verify the ID token against Google's tokeninfo endpoint.
     *
     * @throws BadCredentialsException if the token is invalid or expired
     */
    public GoogleTokenInfo verify(String idToken) {
        String responseBody;
        try {
            responseBody = webClientBuilder.build()
                .get()
                .uri(TOKENINFO_URL, uriBuilder -> uriBuilder
                    .queryParam("id_token", idToken)
                    .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();
        } catch (Exception e) {
            // Non-2xx (invalid/expired token) or network error.
            // Never propagate the raw tokeninfo body to callers.
            log.debug("Google tokeninfo verification failed: {}", e.getClass().getSimpleName());
            throw new BadCredentialsException("Invalid Google credential");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return new GoogleTokenInfo(
                root.path("aud").asText(null),
                root.path("email").asText(null),
                "true".equals(root.path("email_verified").asText()),
                root.path("name").asText(null),
                root.path("picture").asText(null),
                root.path("sub").asText(null)
            );
        } catch (Exception e) {
            log.warn("Failed to parse Google tokeninfo response");
            throw new BadCredentialsException("Invalid Google credential");
        }
    }
}
