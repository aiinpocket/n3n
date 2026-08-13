package com.aiinpocket.n3n.ai.usermemory.service;

import com.aiinpocket.n3n.ai.provider.AssistantAiClient;
import com.aiinpocket.n3n.ai.usermemory.entity.UserMemory;
import com.aiinpocket.n3n.ai.usermemory.repository.UserMemoryRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryExtractionServiceTest extends BaseServiceTest {

    @Mock
    private AssistantAiClient aiClient;

    @Mock
    private UserMemoryService userMemoryService;

    @Mock
    private UserMemoryRepository userMemoryRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MemoryExtractionService service;

    private UUID userId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "everyNMessages", 1);
    }

    private List<Map<String, Object>> messages() {
        return List.of(
            Map.of("role", "user", "content", "我都用 Slack 收通知"),
            Map.of("role", "assistant", "content", "好的，我記下來了")
        );
    }

    private UserMemory memory(String content) {
        return UserMemory.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .content(content)
            .category("preference")
            .source("assistant")
            .createdAt(LocalDateTime.now())
            .build();
    }

    // ==================== parseFacts ====================

    @Test
    @DisplayName("parseFacts parses a valid JSON array")
    void parseFacts_validJson() {
        String raw = "[{\"content\": \"偏好用 Slack 通知\", \"category\": \"preference\"}]";

        List<MemoryExtractionService.ExtractedFact> facts = service.parseFacts(raw);

        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).content()).isEqualTo("偏好用 Slack 通知");
        assertThat(facts.get(0).category()).isEqualTo("preference");
    }

    @Test
    @DisplayName("parseFacts tolerates markdown fences and surrounding prose")
    void parseFacts_markdownFenced() {
        String raw = "以下是萃取結果：\n```json\n[{\"content\": \"正在開發 n3n 專案\", \"category\": \"project\"}]\n```";

        List<MemoryExtractionService.ExtractedFact> facts = service.parseFacts(raw);

        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).content()).isEqualTo("正在開發 n3n 專案");
    }

    @Test
    @DisplayName("parseFacts returns empty list for malformed output")
    void parseFacts_malformed_returnsEmpty() {
        assertThat(service.parseFacts("not json at all")).isEmpty();
        assertThat(service.parseFacts("[{broken json")).isEmpty();
        assertThat(service.parseFacts(null)).isEmpty();
        assertThat(service.parseFacts("")).isEmpty();
        assertThat(service.parseFacts("[]")).isEmpty();
    }

    @Test
    @DisplayName("parseFacts caps results at 3 and skips blank content")
    void parseFacts_capsAtThree() {
        String raw = """
            [{"content": "a1", "category": "fact"},
             {"content": "", "category": "fact"},
             {"content": "a2", "category": "fact"},
             {"content": "a3", "category": "fact"},
             {"content": "a4", "category": "fact"}]
            """;

        List<MemoryExtractionService.ExtractedFact> facts = service.parseFacts(raw);

        assertThat(facts).hasSize(3);
        assertThat(facts).extracting(MemoryExtractionService.ExtractedFact::content)
            .containsExactly("a1", "a2", "a3");
    }

    // ==================== throttle ====================

    @Test
    @DisplayName("shouldExtract triggers only every N calls per conversation")
    void shouldExtract_throttlesPerConversation() {
        ReflectionTestUtils.setField(service, "everyNMessages", 4);

        assertThat(service.shouldExtract(conversationId)).isFalse();
        assertThat(service.shouldExtract(conversationId)).isFalse();
        assertThat(service.shouldExtract(conversationId)).isFalse();
        assertThat(service.shouldExtract(conversationId)).isTrue();
        // counter resets after firing
        assertThat(service.shouldExtract(conversationId)).isFalse();

        // independent conversations have independent counters
        UUID other = UUID.randomUUID();
        assertThat(service.shouldExtract(other)).isFalse();
    }

    // ==================== disabled flag ====================

    @Test
    @DisplayName("onAssistantReply does nothing when disabled")
    void onAssistantReply_disabled_noop() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.onAssistantReply(conversationId, userId, messages());

        verifyNoInteractions(aiClient, userMemoryService, userMemoryRepository);
    }

    @Test
    @DisplayName("onAssistantReply skips when no AI provider is available")
    void onAssistantReply_noProvider_skips() {
        when(aiClient.isAvailable(userId)).thenReturn(false);

        service.onAssistantReply(conversationId, userId, messages());

        verify(aiClient, never()).chat(any(), any(), anyInt(), anyDouble(), any());
        verifyNoInteractions(userMemoryService);
    }

    @Test
    @DisplayName("onAssistantReply extracts and stores when provider available and throttle fires")
    void onAssistantReply_happyPath_stores() {
        when(aiClient.isAvailable(userId)).thenReturn(true);
        when(aiClient.chat(any(), any(), anyInt(), anyDouble(), eq(userId)))
            .thenReturn("[{\"content\": \"偏好用 Slack 通知\", \"category\": \"preference\"}]");
        when(userMemoryRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        service.onAssistantReply(conversationId, userId, messages());

        verify(userMemoryService).add(userId, "偏好用 Slack 通知", "preference", "assistant");
    }

    @Test
    @DisplayName("onAssistantReply never propagates exceptions")
    void onAssistantReply_swallowsExceptions() {
        when(aiClient.isAvailable(userId))
            .thenThrow(new RuntimeException("AI client boom"));

        service.onAssistantReply(conversationId, userId, messages());
        // no exception -> pass
    }

    // ==================== dedup ====================

    @Test
    @DisplayName("dedup skips a fact already contained in an existing memory")
    void dedup_containedInExisting_skips() {
        when(aiClient.chat(any(), any(), anyInt(), anyDouble(), eq(userId)))
            .thenReturn("[{\"content\": \"用 Slack 通知\", \"category\": \"preference\"}]");
        when(userMemoryRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(List.of(memory("使用者偏好用 Slack 通知，不用 email")));

        service.extractAndStore(userId, messages());

        verify(userMemoryService, never()).add(any(), any(), any(), any());
        verify(userMemoryService, never()).update(any(), any(), any(), any());
    }

    @Test
    @DisplayName("dedup updates the existing memory when the new fact is a fuller superset")
    void dedup_supersetOfExisting_updates() {
        UserMemory existing = memory("用 Slack 通知");
        when(aiClient.chat(any(), any(), anyInt(), anyDouble(), eq(userId)))
            .thenReturn("[{\"content\": \"使用者偏好用 Slack 通知，不用 email\", \"category\": \"preference\"}]");
        when(userMemoryRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(List.of(existing));

        service.extractAndStore(userId, messages());

        verify(userMemoryService).update(userId, existing.getId(), "使用者偏好用 Slack 通知，不用 email", "preference");
        verify(userMemoryService, never()).add(any(), any(), any(), any());
    }

    @Test
    @DisplayName("dedup skips near-duplicates with high bigram overlap")
    void dedup_highOverlap_skips() {
        when(aiClient.chat(any(), any(), anyInt(), anyDouble(), eq(userId)))
            .thenReturn("[{\"content\": \"prefers slack notifications for alerts always\", \"category\": \"preference\"}]");
        when(userMemoryRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(List.of(memory("prefers slack notifications for alerts mostly")));

        service.extractAndStore(userId, messages());

        verify(userMemoryService, never()).add(any(), any(), any(), any());
    }

    @Test
    @DisplayName("genuinely new facts are inserted with source assistant")
    void dedup_newFact_inserted() {
        when(aiClient.chat(any(), any(), anyInt(), anyDouble(), eq(userId)))
            .thenReturn("[{\"content\": \"正在開發 n3n 工作流程平台\", \"category\": \"project\"}]");
        when(userMemoryRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(List.of(memory("偏好深色主題")));

        service.extractAndStore(userId, messages());

        verify(userMemoryService).add(userId, "正在開發 n3n 工作流程平台", "project", "assistant");
    }
}
