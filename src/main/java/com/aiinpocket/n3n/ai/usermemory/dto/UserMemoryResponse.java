package com.aiinpocket.n3n.ai.usermemory.dto;

import com.aiinpocket.n3n.ai.usermemory.entity.UserMemory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 記憶回應 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMemoryResponse {

    private UUID id;
    private String content;
    private String category;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserMemoryResponse from(UserMemory memory) {
        return UserMemoryResponse.builder()
            .id(memory.getId())
            .content(memory.getContent())
            .category(memory.getCategory())
            .source(memory.getSource())
            .createdAt(memory.getCreatedAt())
            .updatedAt(memory.getUpdatedAt())
            .build();
    }
}
