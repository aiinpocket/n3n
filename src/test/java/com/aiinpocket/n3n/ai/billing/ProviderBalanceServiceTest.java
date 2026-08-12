package com.aiinpocket.n3n.ai.billing;

import com.aiinpocket.n3n.ai.repository.AiTokenUsageRepository;
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.credential.service.CredentialService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 平台 AI 帳務（管理員專用）：成員用量彙總測試。
 */
class ProviderBalanceServiceTest extends BaseServiceTest {

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private CredentialService credentialService;

    @Mock
    private AiTokenUsageRepository tokenUsageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private WebClient.Builder webClientBuilder;

    @InjectMocks
    private ProviderBalanceService providerBalanceService;

    @Test
    @DisplayName("getUsageByUser：依成員合併多個 model 的用量並帶出 email/name")
    void getUsageByUser_aggregatesAcrossModelsPerUser() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        // 查詢回傳依 user+model 分組的列：userId, model, callCount, inputTokens, outputTokens
        when(tokenUsageRepository.summarizeByUserAndModel(any(Instant.class))).thenReturn(List.<Object[]>of(
                new Object[]{alice, "gpt-4o", 3L, 1_000L, 500L},
                new Object[]{alice, "gpt-4o-mini", 7L, 2_000L, 1_000L},
                new Object[]{bob, "gpt-4o", 1L, 100L, 50L}
        ));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                User.builder().id(alice).email("alice@example.com").name("Alice").build(),
                User.builder().id(bob).email("bob@example.com").name("Bob").build()
        ));

        List<Map<String, Object>> result = providerBalanceService.getUsageByUser(30);

        assertThat(result).hasSize(2);

        Map<String, Object> aliceRow = result.stream()
                .filter(row -> alice.equals(row.get("userId")))
                .findFirst().orElseThrow();
        assertThat(aliceRow.get("email")).isEqualTo("alice@example.com");
        assertThat(aliceRow.get("name")).isEqualTo("Alice");
        assertThat(aliceRow.get("calls")).isEqualTo(10L);
        assertThat(aliceRow.get("inputTokens")).isEqualTo(3_000L);
        assertThat(aliceRow.get("outputTokens")).isEqualTo(1_500L);
        assertThat((Double) aliceRow.get("estimatedCostUsd")).isGreaterThanOrEqualTo(0.0);

        Map<String, Object> bobRow = result.stream()
                .filter(row -> bob.equals(row.get("userId")))
                .findFirst().orElseThrow();
        assertThat(bobRow.get("calls")).isEqualTo(1L);
    }

    @Test
    @DisplayName("getUsageByUser：查無使用者資料時 email/name 為 null，不拋例外")
    void getUsageByUser_missingUserInfo_returnsNullFields() {
        UUID ghost = UUID.randomUUID();
        when(tokenUsageRepository.summarizeByUserAndModel(any(Instant.class))).thenReturn(List.<Object[]>of(
                new Object[]{ghost, "gpt-4o", 2L, 10L, 5L}
        ));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        List<Map<String, Object>> result = providerBalanceService.getUsageByUser(7);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("userId")).isEqualTo(ghost);
        assertThat(result.get(0).get("email")).isNull();
        assertThat(result.get(0).get("name")).isNull();
    }

    @Test
    @DisplayName("getUsageByUser：無用量時回傳空清單")
    void getUsageByUser_noUsage_returnsEmptyList() {
        when(tokenUsageRepository.summarizeByUserAndModel(any(Instant.class))).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        assertThat(providerBalanceService.getUsageByUser(30)).isEmpty();
    }
}
