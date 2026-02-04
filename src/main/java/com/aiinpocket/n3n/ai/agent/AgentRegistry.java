package com.aiinpocket.n3n.ai.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 註冊表
 * 管理所有可用的 Agent
 */
@Slf4j
@Component
public class AgentRegistry {

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    /**
     * 註冊 Agent
     */
    public void register(Agent agent) {
        agents.put(agent.getId(), agent);
        log.info("Registered agent: {} ({})", agent.getId(), agent.getName());
    }

    /**
     * 取得 Agent
     */
    public Agent getAgent(String id) {
        Agent agent = agents.get(id);
        if (agent == null) {
            throw new IllegalArgumentException("Agent not found: " + id);
        }
        return agent;
    }

    /**
     * 取得 Agent（可選）
     */
    public Optional<Agent> findAgent(String id) {
        return Optional.ofNullable(agents.get(id));
    }

    /**
     * 列出所有 Agent
     */
    public List<Agent> listAgents() {
        return new ArrayList<>(agents.values());
    }

    /**
     * 根據能力查找 Agent
     */
    public List<Agent> findAgentsByCapability(String capability) {
        return agents.values().stream()
            .filter(agent -> agent.getCapabilities().contains(capability))
            .toList();
    }

    /**
     * 檢查 Agent 是否存在
     */
    public boolean hasAgent(String id) {
        return agents.containsKey(id);
    }

    /**
     * 移除 Agent
     */
    public boolean unregister(String id) {
        Agent removed = agents.remove(id);
        if (removed != null) {
            log.info("Unregistered agent: {}", id);
            return true;
        }
        return false;
    }
}
