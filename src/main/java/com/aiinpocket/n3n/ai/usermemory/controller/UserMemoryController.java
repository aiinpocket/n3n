package com.aiinpocket.n3n.ai.usermemory.controller;

import com.aiinpocket.n3n.ai.usermemory.dto.UserMemoryRequest;
import com.aiinpocket.n3n.ai.usermemory.dto.UserMemoryResponse;
import com.aiinpocket.n3n.ai.usermemory.service.UserMemoryService;
import com.aiinpocket.n3n.auth.dto.response.UserResponse;
import com.aiinpocket.n3n.auth.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 使用者 AI 記憶 API。
 * 僅能存取自己的記憶（authenticated，非 ADMIN 限定）。
 */
@RestController
@RequestMapping("/api/ai/memory")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI User Memory", description = "Per-user persistent AI memory")
public class UserMemoryController {

    private final UserMemoryService userMemoryService;
    private final AuthService authService;

    /**
     * 列出自己的記憶（新到舊）
     */
    @GetMapping
    public ResponseEntity<List<UserMemoryResponse>> list(Principal principal) {
        UUID userId = requireUserId(principal);
        List<UserMemoryResponse> memories = userMemoryService.list(userId).stream()
            .map(UserMemoryResponse::from)
            .toList();
        return ResponseEntity.ok(memories);
    }

    /**
     * 手動新增一筆記憶（source 固定為 user）
     */
    @PostMapping
    public ResponseEntity<UserMemoryResponse> add(
            @Valid @RequestBody UserMemoryRequest request,
            Principal principal) {
        UUID userId = requireUserId(principal);
        var memory = userMemoryService.add(userId, request.getContent(), request.getCategory(), "user");
        return ResponseEntity.ok(UserMemoryResponse.from(memory));
    }

    /**
     * 更新一筆記憶
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserMemoryResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UserMemoryRequest request,
            Principal principal) {
        UUID userId = requireUserId(principal);
        var memory = userMemoryService.update(userId, id, request.getContent(), request.getCategory());
        return ResponseEntity.ok(UserMemoryResponse.from(memory));
    }

    /**
     * 刪除一筆記憶
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id, Principal principal) {
        UUID userId = requireUserId(principal);
        userMemoryService.delete(userId, id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 清除自己全部的記憶
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteAll(Principal principal) {
        UUID userId = requireUserId(principal);
        userMemoryService.deleteAll(userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private UUID requireUserId(Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required");
        }
        try {
            UserResponse user = authService.getCurrentUser(principal.getName());
            if (user != null && user.getId() != null) {
                return user.getId();
            }
        } catch (Exception e) {
            log.debug("Could not resolve user from principal");
        }
        throw new AccessDeniedException("Authentication required");
    }
}
