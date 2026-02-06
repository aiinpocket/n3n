package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.entity.AiTokenUsage;
import com.aiinpocket.n3n.ai.repository.AiTokenUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTokenUsageService {

    private final AiTokenUsageRepository repository;

    public void record(UUID userId, String provider, String model,
                       int inputTokens, int outputTokens,
                       UUID executionId, String nodeId) {
        try {
            repository.save(AiTokenUsage.builder()
                    .userId(userId)
                    .provider(provider)
                    .model(model)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .executionId(executionId)
                    .nodeId(nodeId)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record token usage: {}", e.getMessage());
        }
    }
}
