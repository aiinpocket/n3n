package com.aiinpocket.n3n.ai.controller;

import com.aiinpocket.n3n.ai.dto.GenerateCodeRequest;
import com.aiinpocket.n3n.ai.dto.GenerateCodeResponse;
import com.aiinpocket.n3n.ai.service.AIAssistantService;
import com.aiinpocket.n3n.auth.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * AI 程式碼生成 API
 */
@RestController
@RequestMapping("/api/ai/code")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Code Generation", description = "AI-powered code generation")
public class AICodeController {

    private final AIAssistantService aiAssistantService;

    /**
     * 生成程式碼
     *
     * @param request 生成請求
     * @param user 當前使用者
     * @return 生成的程式碼
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateCodeResponse> generateCode(
            @RequestBody GenerateCodeRequest request,
            @AuthenticationPrincipal User user) {

        log.info("Code generation request: language={}, description length={}",
            request.getLanguage(),
            request.getDescription() != null ? request.getDescription().length() : 0);

        try {
            GenerateCodeResponse response = aiAssistantService.generateCode(request, user.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Code generation failed", e);
            return ResponseEntity.ok(GenerateCodeResponse.failure(e.getMessage()));
        }
    }
}
