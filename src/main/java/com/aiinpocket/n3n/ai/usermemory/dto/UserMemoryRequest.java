package com.aiinpocket.n3n.ai.usermemory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新增／更新記憶的請求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMemoryRequest {

    @NotBlank(message = "Memory content must not be blank")
    @Size(max = 2000, message = "Memory content must not exceed 2000 characters")
    private String content;

    @Size(max = 32)
    private String category;
}
