package com.aiinpocket.n3n.ai.conversation;

import com.aiinpocket.n3n.ai.entity.Conversation;
import com.aiinpocket.n3n.ai.repository.AiModuleConfigRepository;
import com.aiinpocket.n3n.ai.repository.ConversationRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConversationManagerTest extends BaseServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationSummarizer conversationSummarizer;

    @Mock
    private ContextWindowRegistry contextWindowRegistry;

    @Mock
    private AiModuleConfigRepository aiModuleConfigRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ConversationManager manager;

    private UUID userId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        ReflectionTestUtils.setField(manager, "usableRatio", 0.5);
        ReflectionTestUtils.setField(manager, "compactThreshold", 0.7);
    }

    private Conversation conversationWith(List<Map<String, Object>> messages, String summary) {
        return Conversation.builder()
            .id(conversationId)
            .userId(userId)
            .title("test")
            .messages(new ArrayList<>(messages))
            .summary(summary)
            .messageCount(messages.size())
            .build();
    }

    private Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private void stubRepositories(Conversation conversation) {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubModelLookup() {
        when(aiModuleConfigRepository.findByUserIdAndFeatureAndIsActiveTrue(userId, "default"))
            .thenReturn(Optional.empty());
        when(aiModuleConfigRepository.findByFeatureAndIsActiveTrueOrderByCreatedAtAsc("default"))
            .thenReturn(List.of());
    }

    @Test
    @DisplayName("under the 70% token threshold, no compaction happens")
    void addMessage_underThreshold_noCompaction() {
        // window 10000 -> usable 5000 -> threshold 3500 tokens; tiny messages stay far below
        List<Map<String, Object>> existing = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            existing.add(msg(i % 2 == 0 ? "user" : "assistant", "short message " + i));
        }
        Conversation conversation = conversationWith(existing, null);
        stubRepositories(conversation);
        stubModelLookup();
        when(contextWindowRegistry.windowFor(null)).thenReturn(10_000);

        Conversation result = manager.addMessage(conversationId, userId, "user", "hello there", null);

        verify(conversationSummarizer, never()).summarize(any(), any());
        assertThat(result.getMessages()).hasSize(7);
        assertThat(result.getSummary()).isNull();
    }

    @Test
    @DisplayName("crossing 70% of the usable budget compacts the oldest half and keeps the tail verbatim")
    void addMessage_overThreshold_compactsOldestHalf() {
        // window 1000 -> usable 500 -> threshold 350 tokens.
        // 9 existing messages x ~44 tokens (160 latin chars + 4 overhead) ≈ 396 > 350
        String content160 = "x".repeat(160);
        List<Map<String, Object>> existing = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            existing.add(msg(i % 2 == 0 ? "user" : "assistant", content160 + i));
        }
        Conversation conversation = conversationWith(existing, null);
        stubRepositories(conversation);
        stubModelLookup();
        when(contextWindowRegistry.windowFor(null)).thenReturn(1_000);
        when(conversationSummarizer.summarize(any(), eq(userId))).thenReturn("摘要內容");

        Conversation result = manager.addMessage(conversationId, userId, "user", content160 + "new", null);

        // 10 messages total -> oldest 5 folded, newest 5 kept
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.captor();
        verify(conversationSummarizer).summarize(captor.capture(), eq(userId));
        assertThat(captor.getValue()).hasSize(5);
        assertThat((String) captor.getValue().get(0).get("content")).endsWith("0");

        assertThat(result.getSummary()).isEqualTo("摘要內容");
        assertThat(result.getMessages()).hasSize(5);
        // tail preserved verbatim, newest message last
        assertThat((String) result.getMessages().get(0).get("content")).endsWith("5");
        assertThat((String) result.getMessages().get(4).get("content")).isEqualTo(content160 + "new");
        assertThat(result.getMessageCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("hierarchical compaction feeds the previous summary back into the summarizer")
    void addMessage_compaction_isHierarchical() {
        String content160 = "y".repeat(160);
        List<Map<String, Object>> existing = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            existing.add(msg(i % 2 == 0 ? "user" : "assistant", content160 + i));
        }
        Conversation conversation = conversationWith(existing, "舊摘要");
        stubRepositories(conversation);
        stubModelLookup();
        when(contextWindowRegistry.windowFor(null)).thenReturn(1_000);
        when(conversationSummarizer.summarize(any(), eq(userId))).thenReturn("新摘要");

        Conversation result = manager.addMessage(conversationId, userId, "user", content160 + "new", null);

        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.captor();
        verify(conversationSummarizer).summarize(captor.capture(), eq(userId));
        // first element is the previous summary as a system message
        assertThat(captor.getValue().get(0).get("role")).isEqualTo("system");
        assertThat((String) captor.getValue().get(0).get("content")).contains("舊摘要");
        assertThat(result.getSummary()).isEqualTo("新摘要");
    }

    @Test
    @DisplayName("summarizer failure keeps all messages and does not corrupt the conversation")
    void addMessage_summarizerFails_keepsMessages() {
        String content160 = "z".repeat(160);
        List<Map<String, Object>> existing = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            existing.add(msg(i % 2 == 0 ? "user" : "assistant", content160 + i));
        }
        Conversation conversation = conversationWith(existing, null);
        stubRepositories(conversation);
        stubModelLookup();
        when(contextWindowRegistry.windowFor(null)).thenReturn(1_000);
        when(conversationSummarizer.summarize(any(), eq(userId))).thenReturn("");

        Conversation result = manager.addMessage(conversationId, userId, "user", content160 + "new", null);

        assertThat(result.getMessages()).hasSize(10);
        assertThat(result.getSummary()).isNull();
    }

    @Test
    @DisplayName("exceeding the 200-message hard cap compacts even when under token budget")
    void addMessage_overHardCap_compacts() {
        List<Map<String, Object>> existing = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            existing.add(msg(i % 2 == 0 ? "user" : "assistant", "m" + i));
        }
        Conversation conversation = conversationWith(existing, null);
        stubRepositories(conversation);
        // No window/model stubs needed: the hard cap triggers before the token budget is consulted
        when(conversationSummarizer.summarize(any(), eq(userId))).thenReturn("摘要");

        Conversation result = manager.addMessage(conversationId, userId, "user", "the 201st", null);

        verify(conversationSummarizer).summarize(any(), eq(userId));
        assertThat(result.getMessages()).hasSize(101);
        assertThat((String) result.getMessages().get(100).get("content")).isEqualTo("the 201st");
    }

    @Test
    @DisplayName("getContextForAI returns summary as system message plus remaining messages")
    void getContextForAI_summaryFirst() {
        List<Map<String, Object>> existing = List.of(msg("user", "hi"), msg("assistant", "hello"));
        Conversation conversation = conversationWith(existing, "先前摘要");
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        List<Map<String, Object>> context = manager.getContextForAI(conversationId, userId);

        assertThat(context).hasSize(3);
        assertThat(context.get(0).get("role")).isEqualTo("system");
        assertThat((String) context.get(0).get("content")).contains("先前摘要");
        assertThat(context.get(1).get("content")).isEqualTo("hi");
    }
}
