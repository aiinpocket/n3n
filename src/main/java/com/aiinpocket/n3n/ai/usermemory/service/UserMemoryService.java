package com.aiinpocket.n3n.ai.usermemory.service;

import com.aiinpocket.n3n.ai.usermemory.entity.UserMemory;
import com.aiinpocket.n3n.ai.usermemory.repository.UserMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 使用者長期記憶服務。
 * 提供記憶的增刪查改、每人數量上限（滿了淘汰最舊的一筆），
 * 以及組裝給 AI 的精簡記憶區塊。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserMemoryService {

    /** 允許的分類 */
    public static final Set<String> ALLOWED_CATEGORIES =
        Set.of("preference", "fact", "project", "style", "general");

    /** 允許的來源 */
    public static final Set<String> ALLOWED_SOURCES = Set.of("assistant", "user");

    /** 單筆記憶內容上限（字元） */
    public static final int MAX_CONTENT_LENGTH = 2000;

    /** 組裝記憶上下文的長度上限（字元） */
    static final int MAX_CONTEXT_CHARS = 1500;

    private final UserMemoryRepository repository;

    @Value("${n3n.ai.user-memory.max-entries:200}")
    private int maxEntries;

    /**
     * 列出使用者全部記憶（新到舊）
     */
    @Transactional(readOnly = true)
    public List<UserMemory> list(UUID userId) {
        requireUserId(userId);
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 新增一筆記憶。超過上限時先淘汰最舊的一筆。
     */
    @Transactional
    public UserMemory add(UUID userId, String content, String category, String source) {
        requireUserId(userId);
        String cleanContent = validateContent(content);
        String cleanCategory = normalizeCategory(category);
        String cleanSource = normalizeSource(source);

        // 滿了淘汰最舊的記憶，讓新的進得來
        while (repository.countByUserId(userId) >= maxEntries) {
            Optional<UserMemory> oldest = repository.findFirstByUserIdOrderByCreatedAtAsc(userId);
            if (oldest.isEmpty()) {
                break;
            }
            repository.delete(oldest.get());
            log.debug("Evicted oldest memory {} for user {}", oldest.get().getId(), userId);
        }

        UserMemory memory = UserMemory.builder()
            .userId(userId)
            .content(cleanContent)
            .category(cleanCategory)
            .source(cleanSource)
            .build();

        return repository.save(memory);
    }

    /**
     * 更新一筆記憶（僅限本人）
     */
    @Transactional
    public UserMemory update(UUID userId, UUID memoryId, String content, String category) {
        requireUserId(userId);
        UserMemory existing = repository.findByIdAndUserId(memoryId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Memory not found"));

        UserMemory updated = UserMemory.builder()
            .id(existing.getId())
            .userId(existing.getUserId())
            .content(content != null ? validateContent(content) : existing.getContent())
            .category(category != null ? normalizeCategory(category) : existing.getCategory())
            .source(existing.getSource())
            .createdAt(existing.getCreatedAt())
            .build();

        return repository.save(updated);
    }

    /**
     * 刪除一筆記憶（僅限本人）
     */
    @Transactional
    public void delete(UUID userId, UUID memoryId) {
        requireUserId(userId);
        UserMemory existing = repository.findByIdAndUserId(memoryId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        repository.delete(existing);
    }

    /**
     * 清除使用者全部記憶
     */
    @Transactional
    public void deleteAll(UUID userId) {
        requireUserId(userId);
        repository.deleteByUserId(userId);
    }

    /**
     * 組裝給 AI 的記憶區塊。
     * 新到舊的條列清單（含分類），總長不超過 {@link #MAX_CONTEXT_CHARS}；
     * 沒有記憶時回傳空字串。
     */
    @Transactional(readOnly = true)
    public String buildMemoryContext(UUID userId) {
        if (userId == null) {
            return "";
        }

        List<UserMemory> memories = repository.findByUserIdOrderByCreatedAtDesc(userId);
        if (memories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (UserMemory memory : memories) {
            String line = "- [" + memory.getCategory() + "] " + memory.getContent() + "\n";
            if (sb.length() + line.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            sb.append(line);
        }

        return sb.toString().trim();
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
    }

    private String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Memory content must not be blank");
        }
        String trimmed = content.trim();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException(
                "Memory content must not exceed " + MAX_CONTENT_LENGTH + " characters");
        }
        return trimmed;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "general";
        }
        String lower = category.trim().toLowerCase();
        return ALLOWED_CATEGORIES.contains(lower) ? lower : "general";
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "assistant";
        }
        String lower = source.trim().toLowerCase();
        return ALLOWED_SOURCES.contains(lower) ? lower : "assistant";
    }
}
