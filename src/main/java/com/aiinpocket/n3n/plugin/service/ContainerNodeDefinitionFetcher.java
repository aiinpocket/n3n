package com.aiinpocket.n3n.plugin.service;

import com.aiinpocket.n3n.plugin.dto.ContainerNodeDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Container Node Definition Fetcher - Fetches node definitions from running plugin containers.
 * Used during plugin installation to auto-register nodes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContainerNodeDefinitionFetcher {

    private final ObjectMapper objectMapper;
    private final PluginNodeRegistrar pluginNodeRegistrar;

    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String NODE_DEFINITIONS_ENDPOINT = "/n3n/node-definitions";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Fetch node definitions from a container and register them.
     *
     * @param containerId Container ID
     * @param containerPort Container port
     * @param nodeType Expected node type (for fallback)
     * @return true if successful, false otherwise
     */
    public boolean fetchAndRegisterNodes(String containerId, int containerPort, String nodeType) {
        log.info("Fetching node definitions from container {} on port {}", containerId, containerPort);

        Optional<List<ContainerNodeDefinition>> definitions = fetchWithRetry(containerPort);

        if (definitions.isEmpty()) {
            log.warn("Failed to fetch node definitions from container {}, using fallback", containerId);
            // Create a minimal fallback definition
            ContainerNodeDefinition fallback = createFallbackDefinition(containerId, containerPort, nodeType);
            registerNode(fallback);
            return true;
        }

        List<ContainerNodeDefinition> nodeDefinitions = definitions.get();
        log.info("Fetched {} node definitions from container {}", nodeDefinitions.size(), containerId);

        // Register each node
        for (ContainerNodeDefinition definition : nodeDefinitions) {
            // Set container metadata
            definition.setContainer(ContainerNodeDefinition.ContainerMetadata.builder()
                    .containerId(containerId)
                    .port(containerPort)
                    .healthEndpoint("/health")
                    .build());

            registerNode(definition);
        }

        return true;
    }

    /**
     * Fetch node definitions with retry logic.
     */
    private Optional<List<ContainerNodeDefinition>> fetchWithRetry(int containerPort) {
        String url = "http://localhost:" + containerPort + NODE_DEFINITIONS_ENDPOINT;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.debug("Attempting to fetch node definitions (attempt {}/{})", attempt, MAX_RETRIES);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .header("Accept", "application/json")
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<ContainerNodeDefinition> definitions = objectMapper.readValue(
                            response.body(),
                            new TypeReference<>() {}
                    );
                    return Optional.of(definitions);
                }

                log.warn("Received status {} from container, attempt {}/{}",
                        response.statusCode(), attempt, MAX_RETRIES);

            } catch (Exception e) {
                log.warn("Failed to fetch node definitions (attempt {}/{}): {}",
                        attempt, MAX_RETRIES, e.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Create a fallback node definition when container doesn't provide one.
     */
    private ContainerNodeDefinition createFallbackDefinition(
            String containerId, int containerPort, String nodeType) {

        return ContainerNodeDefinition.builder()
                .type(nodeType)
                .displayName(formatDisplayName(nodeType))
                .description("由容器提供的節點: " + nodeType)
                .category("plugin")
                .icon("plugin")
                .isTrigger(false)
                .supportsAsync(true)
                .configSchema(Map.of(
                        "type", "object",
                        "properties", Map.of()
                ))
                .keywords(List.of(nodeType, "plugin", "container"))
                .version("1.0.0")
                .container(ContainerNodeDefinition.ContainerMetadata.builder()
                        .containerId(containerId)
                        .port(containerPort)
                        .healthEndpoint("/health")
                        .build())
                .build();
    }

    /**
     * Register a node definition with the system.
     */
    private void registerNode(ContainerNodeDefinition definition) {
        try {
            log.info("Registering node: {} ({})", definition.getType(), definition.getDisplayName());

            // Convert to the format expected by PluginNodeRegistrar
            Map<String, Object> nodeInfo = Map.of(
                    "type", definition.getType(),
                    "displayName", definition.getDisplayName(),
                    "description", definition.getDescription(),
                    "category", definition.getCategory(),
                    "icon", definition.getIcon(),
                    "isTrigger", definition.isTrigger(),
                    "supportsAsync", definition.isSupportsAsync(),
                    "configSchema", definition.getConfigSchema() != null ?
                            definition.getConfigSchema() : Map.of(),
                    "container", definition.getContainer() != null ? Map.of(
                            "containerId", definition.getContainer().getContainerId(),
                            "port", definition.getContainer().getPort(),
                            "healthEndpoint", definition.getContainer().getHealthEndpoint()
                    ) : Map.of()
            );

            pluginNodeRegistrar.registerDynamicNode(nodeInfo);

            log.info("Successfully registered node: {}", definition.getType());
        } catch (Exception e) {
            log.error("Failed to register node {}: {}", definition.getType(), e.getMessage(), e);
        }
    }

    /**
     * Format node type to display name.
     */
    private String formatDisplayName(String nodeType) {
        if (nodeType == null || nodeType.isBlank()) {
            return "未知節點";
        }

        // Convert camelCase or snake_case to readable format
        String formatted = nodeType
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("_", " ");

        // Capitalize first letter
        return formatted.substring(0, 1).toUpperCase() + formatted.substring(1);
    }

    /**
     * Check if a container is providing node definitions.
     */
    public boolean hasNodeDefinitions(int containerPort) {
        String url = "http://localhost:" + containerPort + NODE_DEFINITIONS_ENDPOINT;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
