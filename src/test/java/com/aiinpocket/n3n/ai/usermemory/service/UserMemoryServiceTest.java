package com.aiinpocket.n3n.ai.usermemory.service;

import com.aiinpocket.n3n.ai.usermemory.entity.UserMemory;
import com.aiinpocket.n3n.ai.usermemory.repository.UserMemoryRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserMemoryServiceTest extends BaseServiceTest {

    @Mock
    private UserMemoryRepository repository;

    @InjectMocks
    private UserMemoryService service;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        ReflectionTestUtils.setField(service, "maxEntries", 3);
    }

    private UserMemory memory(String content, String category) {
        return UserMemory.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .content(content)
            .category(category)
            .source("assistant")
            .createdAt(LocalDateTime.now())
            .build();
    }

    // ==================== add ====================

    @Test
    @DisplayName("add saves memory with normalized category and source")
    void add_savesWithNormalizedFields() {
        when(repository.countByUserId(userId)).thenReturn(0L);
        when(repository.save(any(UserMemory.class))).thenAnswer(inv -> inv.getArgument(0));

        UserMemory saved = service.add(userId, "  likes Slack  ", "PREFERENCE", "assistant");

        assertThat(saved.getContent()).isEqualTo("likes Slack");
        assertThat(saved.getCategory()).isEqualTo("preference");
        assertThat(saved.getSource()).isEqualTo("assistant");
        assertThat(saved.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("add falls back to general category and assistant source for unknown values")
    void add_unknownCategoryAndSource_fallsBack() {
        when(repository.countByUserId(userId)).thenReturn(0L);
        when(repository.save(any(UserMemory.class))).thenAnswer(inv -> inv.getArgument(0));

        UserMemory saved = service.add(userId, "content", "banana", "hacker");

        assertThat(saved.getCategory()).isEqualTo("general");
        assertThat(saved.getSource()).isEqualTo("assistant");
    }

    @Test
    @DisplayName("add rejects blank content")
    void add_blankContent_throws() {
        assertThatThrownBy(() -> service.add(userId, "   ", "fact", "user"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("add rejects content over 2000 characters")
    void add_tooLongContent_throws() {
        String longContent = "x".repeat(2001);
        assertThatThrownBy(() -> service.add(userId, longContent, "fact", "user"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("add rejects null userId")
    void add_nullUserId_throws() {
        assertThatThrownBy(() -> service.add(null, "content", "fact", "user"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== cap eviction ====================

    @Test
    @DisplayName("add evicts oldest memory when cap reached")
    void add_atCap_evictsOldest() {
        UserMemory oldest = memory("oldest", "general");
        when(repository.countByUserId(userId)).thenReturn(3L, 2L);
        when(repository.findFirstByUserIdOrderByCreatedAtAsc(userId)).thenReturn(Optional.of(oldest));
        when(repository.save(any(UserMemory.class))).thenAnswer(inv -> inv.getArgument(0));

        service.add(userId, "new memory", "fact", "user");

        verify(repository).delete(oldest);
        verify(repository).save(any(UserMemory.class));
    }

    @Test
    @DisplayName("add does not evict when under cap")
    void add_underCap_noEviction() {
        when(repository.countByUserId(userId)).thenReturn(2L);
        when(repository.save(any(UserMemory.class))).thenAnswer(inv -> inv.getArgument(0));

        service.add(userId, "new memory", "fact", "user");

        verify(repository, never()).delete(any());
    }

    // ==================== ownership ====================

    @Test
    @DisplayName("delete removes memory owned by the user")
    void delete_owned_deletes() {
        UserMemory owned = memory("mine", "fact");
        when(repository.findByIdAndUserId(owned.getId(), userId)).thenReturn(Optional.of(owned));

        service.delete(userId, owned.getId());

        verify(repository).delete(owned);
    }

    @Test
    @DisplayName("delete throws when memory does not belong to the user")
    void delete_notOwned_throws() {
        UUID memoryId = UUID.randomUUID();
        when(repository.findByIdAndUserId(memoryId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, memoryId))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).delete(any(UserMemory.class));
    }

    @Test
    @DisplayName("update throws when memory does not belong to the user")
    void update_notOwned_throws() {
        UUID memoryId = UUID.randomUUID();
        when(repository.findByIdAndUserId(memoryId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(userId, memoryId, "new", "fact"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("update keeps existing content and category when null passed")
    void update_partialUpdate_keepsExisting() {
        UserMemory existing = memory("original", "preference");
        when(repository.findByIdAndUserId(existing.getId(), userId)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserMemory.class))).thenAnswer(inv -> inv.getArgument(0));

        UserMemory updated = service.update(userId, existing.getId(), null, null);

        assertThat(updated.getContent()).isEqualTo("original");
        assertThat(updated.getCategory()).isEqualTo("preference");
    }

    @Test
    @DisplayName("deleteAll delegates to repository")
    void deleteAll_delegates() {
        service.deleteAll(userId);
        verify(repository).deleteByUserId(userId);
    }

    // ==================== buildMemoryContext ====================

    @Test
    @DisplayName("buildMemoryContext formats bulleted list with categories")
    void buildMemoryContext_formatsBulletedList() {
        when(repository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(
            memory("newest fact", "fact"),
            memory("a preference", "preference")
        ));

        String context = service.buildMemoryContext(userId);

        assertThat(context).contains("- [fact] newest fact");
        assertThat(context).contains("- [preference] a preference");
        assertThat(context.indexOf("newest fact")).isLessThan(context.indexOf("a preference"));
    }

    @Test
    @DisplayName("buildMemoryContext returns empty string when no memories")
    void buildMemoryContext_empty_returnsEmptyString() {
        when(repository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        assertThat(service.buildMemoryContext(userId)).isEmpty();
    }

    @Test
    @DisplayName("buildMemoryContext returns empty string for null userId")
    void buildMemoryContext_nullUserId_returnsEmptyString() {
        assertThat(service.buildMemoryContext(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("buildMemoryContext truncates at the character limit")
    void buildMemoryContext_truncatesAtLimit() {
        String longContent = "y".repeat(400);
        List<UserMemory> many = List.of(
            memory(longContent, "fact"),
            memory(longContent, "fact"),
            memory(longContent, "fact"),
            memory(longContent, "fact"),
            memory(longContent, "fact"),
            memory(longContent, "fact")
        );
        when(repository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(many);

        String context = service.buildMemoryContext(userId);

        assertThat(context.length()).isLessThanOrEqualTo(UserMemoryService.MAX_CONTEXT_CHARS);
        // 只裝得下前 3 筆（每筆約 411 字元）
        assertThat(context.split("\n")).hasSize(3);
    }
}
