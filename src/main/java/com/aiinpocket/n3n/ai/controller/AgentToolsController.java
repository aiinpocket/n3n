package com.aiinpocket.n3n.ai.controller;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeToolRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 列出 AI Agent 節點可用的工具（id、名稱、描述、分類、是否需確認）。
 *
 * 供前端（或使用者）查詢完整工具清單。僅回傳靜態 metadata，
 * 不含任何秘密，一般登入成員即可查詢（非 ADMIN 專屬端點）。
 */
@RestController
@RequestMapping("/api/ai/agent-tools")
@RequiredArgsConstructor
@Tag(name = "Agent Tools", description = "Tools available to the AI Agent node")
public class AgentToolsController {

    private final AgentNodeToolRegistry toolRegistry;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAgentTools() {
        List<Map<String, Object>> summaries = toolRegistry.getToolSummaries().stream()
                .sorted(Comparator.comparing(s -> String.valueOf(s.get("id"))))
                .toList();
        return ResponseEntity.ok(summaries);
    }
}
