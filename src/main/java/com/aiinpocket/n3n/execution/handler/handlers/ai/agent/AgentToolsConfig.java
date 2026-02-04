package com.aiinpocket.n3n.execution.handler.handlers.ai.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 工具自動註冊配置
 * 在應用啟動時自動註冊所有內建工具
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AgentToolsConfig {

    private final AgentNodeToolRegistry toolRegistry;
    private final List<AgentNodeTool> tools;

    @EventListener(ApplicationReadyEvent.class)
    public void registerBuiltInTools() {
        log.info("Registering {} built-in agent tools...", tools.size());

        for (AgentNodeTool tool : tools) {
            toolRegistry.register(tool);
        }

        log.info("Agent tools registered: {}", toolRegistry.getAllTools().stream()
            .map(AgentNodeTool::getId)
            .toList());
    }
}
