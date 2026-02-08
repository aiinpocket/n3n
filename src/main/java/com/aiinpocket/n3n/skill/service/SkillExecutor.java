package com.aiinpocket.n3n.skill.service;

import com.aiinpocket.n3n.skill.SkillResult;
import com.aiinpocket.n3n.skill.entity.Skill;
import com.aiinpocket.n3n.skill.entity.SkillExecution;
import com.aiinpocket.n3n.skill.repository.SkillExecutionRepository;
import com.aiinpocket.n3n.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Executes skills based on their implementation type.
 * All execution is pure code - no AI involved at runtime.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillExecutor {

    private final SkillRepository skillRepository;
    private final SkillExecutionRepository skillExecutionRepository;
    private final BuiltinSkillRegistry builtinSkillRegistry;
    private final WebClient.Builder webClientBuilder;

    /**
     * Execute a skill by ID.
     */
    public SkillResult execute(UUID skillId, Map<String, Object> input, UUID executionId, UUID nodeExecutionId, UUID userId) {
        Optional<Skill> skillOpt = skillRepository.findById(skillId);
        if (skillOpt.isEmpty()) {
            return SkillResult.failure("SKILL_NOT_FOUND", "Skill not found: " + skillId);
        }

        Skill skill = skillOpt.get();
        return executeSkill(skill, input, executionId, nodeExecutionId, userId);
    }

    /**
     * Execute a skill by name.
     */
    public SkillResult executeByName(String skillName, Map<String, Object> input, UUID executionId, UUID nodeExecutionId, UUID userId) {
        Optional<Skill> skillOpt = skillRepository.findByName(skillName);
        if (skillOpt.isEmpty()) {
            return SkillResult.failure("SKILL_NOT_FOUND", "Skill not found: " + skillName);
        }

        Skill skill = skillOpt.get();
        return executeSkill(skill, input, executionId, nodeExecutionId, userId);
    }

    private SkillResult executeSkill(Skill skill, Map<String, Object> input, UUID executionId, UUID nodeExecutionId, UUID userId) {
        Instant startTime = Instant.now();
        SkillResult result;

        try {
            log.debug("Executing skill: {} (type: {})", skill.getName(), skill.getImplementationType());

            result = switch (skill.getImplementationType()) {
                case "java" -> executeJavaSkill(skill, input);
                case "http" -> executeHttpSkill(skill, input);
                case "script" -> executeScriptSkill(skill, input);
                default -> SkillResult.failure("UNKNOWN_TYPE", "Unknown implementation type: " + skill.getImplementationType());
            };

        } catch (Exception e) {
            log.error("Error executing skill {}: {}", skill.getName(), e.getMessage(), e);
            result = SkillResult.failure(e);
        }

        // Record execution
        int durationMs = (int) (Instant.now().toEpochMilli() - startTime.toEpochMilli());
        recordExecution(skill, input, result, executionId, nodeExecutionId, userId, durationMs);

        return result;
    }

    private SkillResult executeJavaSkill(Skill skill, Map<String, Object> input) {
        // For Java skills, delegate to the built-in registry
        if (skill.getIsBuiltin()) {
            return builtinSkillRegistry.executeSkill(skill.getName(), input);
        }

        // Custom Java skills would need class loading - not supported yet
        return SkillResult.failure("NOT_SUPPORTED", "Custom Java skills not yet supported");
    }

    private static final java.time.Duration SKILL_HTTP_TIMEOUT = java.time.Duration.ofSeconds(30);

    private SkillResult executeHttpSkill(Skill skill, Map<String, Object> input) {
        // HTTP skills call an external API
        Map<String, Object> config = skill.getImplementationConfig();
        String url = (String) config.get("url");
        String method = (String) config.getOrDefault("method", "POST");

        if (url == null || url.isBlank()) {
            return SkillResult.failure("CONFIG_ERROR", "HTTP skill missing URL configuration");
        }

        // SSRF protection: block internal network addresses
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host != null && isInternalAddress(host)) {
                return SkillResult.failure("SSRF_BLOCKED", "Access to internal network addresses is not allowed");
            }
        } catch (java.net.URISyntaxException e) {
            return SkillResult.failure("CONFIG_ERROR", "Invalid URL: " + url);
        }

        try {
            WebClient webClient = webClientBuilder.build();

            @SuppressWarnings("unchecked")
            Mono<Map> responseMono = switch (method.toUpperCase()) {
                case "GET" -> webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class);
                case "POST" -> webClient.post()
                    .uri(url)
                    .bodyValue(input)
                    .retrieve()
                    .bodyToMono(Map.class);
                case "PUT" -> webClient.put()
                    .uri(url)
                    .bodyValue(input)
                    .retrieve()
                    .bodyToMono(Map.class);
                case "DELETE" -> webClient.delete()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class);
                default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            };

            @SuppressWarnings("unchecked")
            Map<String, Object> response = responseMono.timeout(SKILL_HTTP_TIMEOUT).block();
            return SkillResult.success(response != null ? response : Map.of());
        } catch (Exception e) {
            return SkillResult.failure("HTTP_ERROR", "HTTP request failed: " + e.getMessage());
        }
    }

    private boolean isInternalAddress(String host) {
        String lower = host.toLowerCase();
        return lower.equals("localhost") || lower.equals("127.0.0.1") ||
               lower.equals("0.0.0.0") || lower.equals("::1") ||
               lower.equals("169.254.169.254") || // Cloud metadata
               lower.startsWith("10.") ||
               lower.startsWith("192.168.") ||
               lower.matches("172\\.(1[6-9]|2\\d|3[01])\\..*") ||
               lower.endsWith(".internal") || lower.endsWith(".local");
    }

    private SkillResult executeScriptSkill(Skill skill, Map<String, Object> input) {
        // Script skills would execute a script - not supported yet
        return SkillResult.failure("NOT_SUPPORTED", "Script skills not yet supported");
    }

    private void recordExecution(Skill skill, Map<String, Object> input, SkillResult result,
                                  UUID executionId, UUID nodeExecutionId, UUID userId, int durationMs) {
        try {
            SkillExecution execution = SkillExecution.builder()
                .skillId(skill.getId())
                .skillName(skill.getName())
                .executionId(executionId)
                .nodeExecutionId(nodeExecutionId)
                .inputData(input)
                .outputData(result.isSuccess() ? result.getData() : null)
                .status(result.isSuccess() ? "COMPLETED" : "FAILED")
                .errorMessage(result.getErrorMessage())
                .durationMs(durationMs)
                .executedBy(userId)
                .build();

            skillExecutionRepository.save(execution);
        } catch (Exception e) {
            log.warn("Failed to record skill execution: {}", e.getMessage());
        }
    }
}
