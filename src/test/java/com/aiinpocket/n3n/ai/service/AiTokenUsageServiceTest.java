package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.entity.AiTokenUsage;
import com.aiinpocket.n3n.ai.repository.AiTokenUsageRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiTokenUsageServiceTest extends BaseServiceTest {

    @Mock
    private AiTokenUsageRepository repository;

    @InjectMocks
    private AiTokenUsageService aiTokenUsageService;

    private UUID userId;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        executionId = UUID.randomUUID();
    }

    @Test
    @DisplayName("record saves entity with correct fields")
    void record_success_savesEntity() {
        when(repository.save(any(AiTokenUsage.class))).thenAnswer(inv -> inv.getArgument(0));

        aiTokenUsageService.record(userId, "openai", "gpt-4", 100, 50, executionId, "node1");

        ArgumentCaptor<AiTokenUsage> captor = ArgumentCaptor.forClass(AiTokenUsage.class);
        verify(repository).save(captor.capture());

        AiTokenUsage saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getProvider()).isEqualTo("openai");
        assertThat(saved.getModel()).isEqualTo("gpt-4");
        assertThat(saved.getInputTokens()).isEqualTo(100);
        assertThat(saved.getOutputTokens()).isEqualTo(50);
        assertThat(saved.getExecutionId()).isEqualTo(executionId);
        assertThat(saved.getNodeId()).isEqualTo("node1");
    }

    @Test
    @DisplayName("record with exception does not throw")
    void record_withException_doesNotThrow() {
        when(repository.save(any(AiTokenUsage.class)))
                .thenThrow(new RuntimeException("DB connection failed"));

        assertThatCode(() ->
                aiTokenUsageService.record(userId, "openai", "gpt-4", 100, 50, executionId, "node1")
        ).doesNotThrowAnyException();

        verify(repository).save(any(AiTokenUsage.class));
    }

    @Test
    @DisplayName("record with all fields populated saves correctly")
    void record_allFieldsPopulated() {
        UUID specificUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID specificExecId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        when(repository.save(any(AiTokenUsage.class))).thenAnswer(inv -> inv.getArgument(0));

        aiTokenUsageService.record(specificUserId, "anthropic", "claude-3-opus", 500, 200,
                specificExecId, "ai-node-42");

        ArgumentCaptor<AiTokenUsage> captor = ArgumentCaptor.forClass(AiTokenUsage.class);
        verify(repository).save(captor.capture());

        AiTokenUsage saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(specificUserId);
        assertThat(saved.getProvider()).isEqualTo("anthropic");
        assertThat(saved.getModel()).isEqualTo("claude-3-opus");
        assertThat(saved.getInputTokens()).isEqualTo(500);
        assertThat(saved.getOutputTokens()).isEqualTo(200);
        assertThat(saved.getExecutionId()).isEqualTo(specificExecId);
        assertThat(saved.getNodeId()).isEqualTo("ai-node-42");
    }

    @Test
    @DisplayName("record with null optional fields saves successfully")
    void record_withNullOptionalFields() {
        when(repository.save(any(AiTokenUsage.class))).thenAnswer(inv -> inv.getArgument(0));

        aiTokenUsageService.record(userId, "openai", "gpt-3.5-turbo", 10, 5, null, null);

        ArgumentCaptor<AiTokenUsage> captor = ArgumentCaptor.forClass(AiTokenUsage.class);
        verify(repository).save(captor.capture());

        AiTokenUsage saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getProvider()).isEqualTo("openai");
        assertThat(saved.getModel()).isEqualTo("gpt-3.5-turbo");
        assertThat(saved.getInputTokens()).isEqualTo(10);
        assertThat(saved.getOutputTokens()).isEqualTo(5);
        assertThat(saved.getExecutionId()).isNull();
        assertThat(saved.getNodeId()).isNull();
    }

    @Test
    @DisplayName("record with zero tokens saves correctly")
    void record_zeroTokens() {
        when(repository.save(any(AiTokenUsage.class))).thenAnswer(inv -> inv.getArgument(0));

        aiTokenUsageService.record(userId, "openai", "gpt-4", 0, 0, executionId, "node1");

        ArgumentCaptor<AiTokenUsage> captor = ArgumentCaptor.forClass(AiTokenUsage.class);
        verify(repository).save(captor.capture());

        AiTokenUsage saved = captor.getValue();
        assertThat(saved.getInputTokens()).isZero();
        assertThat(saved.getOutputTokens()).isZero();
    }

    @Test
    @DisplayName("record calls repository.save exactly once")
    void record_callsSaveOnce() {
        when(repository.save(any(AiTokenUsage.class))).thenAnswer(inv -> inv.getArgument(0));

        aiTokenUsageService.record(userId, "openai", "gpt-4", 100, 50, executionId, "node1");

        verify(repository, times(1)).save(any(AiTokenUsage.class));
        verifyNoMoreInteractions(repository);
    }
}
