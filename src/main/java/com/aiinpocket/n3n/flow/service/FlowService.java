package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.common.constant.Status;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.handler.NodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.flow.dto.*;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowShareRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.service.ExternalServiceService;
import com.aiinpocket.n3n.service.dto.EndpointSchemaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlowService {

    private final FlowRepository flowRepository;
    private final FlowShareRepository flowShareRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final DagParser dagParser;
    private final NodeHandlerRegistry nodeHandlerRegistry;
    private final ExternalServiceService externalServiceService;

    public Page<FlowResponse> listFlows(UUID userId, Pageable pageable) {
        Page<Flow> flowPage = flowRepository.findByCreatedByAndIsDeletedFalse(userId, pageable);
        return toFlowResponsePage(flowPage);
    }

    public Page<FlowResponse> searchFlows(UUID userId, String query, Pageable pageable) {
        if (query == null || query.trim().isEmpty()) {
            return listFlows(userId, pageable);
        }
        Page<Flow> flowPage = flowRepository.searchByCreatedByAndQuery(userId, query.trim(), pageable);
        return toFlowResponsePage(flowPage);
    }

    /**
     * 批次轉換 Flow Page 為 FlowResponse Page（解決 N+1 問題）
     */
    private Page<FlowResponse> toFlowResponsePage(Page<Flow> flowPage) {
        if (flowPage.isEmpty()) {
            return flowPage.map(FlowResponse::from);
        }

        // 批次查詢所有版本
        List<UUID> flowIds = flowPage.getContent().stream()
            .map(Flow::getId)
            .toList();

        List<FlowVersion> allVersions = flowVersionRepository
            .findByFlowIdInOrderByFlowIdAscCreatedAtDesc(flowIds);

        // 建立 flowId -> 版本列表 的對應
        Map<UUID, List<FlowVersion>> versionsByFlowId = allVersions.stream()
            .collect(Collectors.groupingBy(FlowVersion::getFlowId));

        // 轉換為 FlowResponse
        return flowPage.map(flow -> {
            List<FlowVersion> versions = versionsByFlowId.getOrDefault(flow.getId(), List.of());
            String latestVersion = versions.isEmpty() ? null : versions.get(0).getVersion();
            String publishedVersion = versions.stream()
                .filter(v -> Status.FlowVersion.PUBLISHED.equals(v.getStatus()))
                .findFirst()
                .map(FlowVersion::getVersion)
                .orElse(null);
            return FlowResponse.from(flow, latestVersion, publishedVersion);
        });
    }

    /**
     * 單一 Flow 轉換（用於 getFlow 等單一查詢場景）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Map<String, Object> original) {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                copy.put(entry.getKey(), deepCopyMap((Map<String, Object>) value));
            } else if (value instanceof java.util.List<?> list) {
                copy.put(entry.getKey(), deepCopyList(list));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Object> deepCopyList(java.util.List<?> original) {
        java.util.List<Object> copy = new java.util.ArrayList<>();
        for (Object item : original) {
            if (item instanceof Map) {
                copy.add(deepCopyMap((Map<String, Object>) item));
            } else if (item instanceof java.util.List<?> list) {
                copy.add(deepCopyList(list));
            } else {
                copy.add(item);
            }
        }
        return copy;
    }

    private FlowResponse toFlowResponse(Flow flow) {
        List<FlowVersion> versions = flowVersionRepository.findByFlowIdOrderByCreatedAtDesc(flow.getId());
        String latestVersion = versions.isEmpty() ? null : versions.get(0).getVersion();
        String publishedVersion = versions.stream()
            .filter(v -> Status.FlowVersion.PUBLISHED.equals(v.getStatus()))
            .findFirst()
            .map(FlowVersion::getVersion)
            .orElse(null);
        return FlowResponse.from(flow, latestVersion, publishedVersion);
    }

    public FlowResponse getFlow(UUID id) {
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + id));

        List<FlowVersion> versions = flowVersionRepository.findByFlowIdOrderByCreatedAtDesc(id);
        String latestVersion = versions.isEmpty() ? null : versions.get(0).getVersion();
        String publishedVersion = versions.stream()
            .filter(v -> Status.FlowVersion.PUBLISHED.equals(v.getStatus()))
            .findFirst()
            .map(FlowVersion::getVersion)
            .orElse(null);

        return FlowResponse.from(flow, latestVersion, publishedVersion);
    }

    @Transactional
    public FlowResponse createFlow(CreateFlowRequest request, UUID userId) {
        if (flowRepository.existsByNameAndCreatedByAndIsDeletedFalse(request.getName(), userId)) {
            throw new IllegalArgumentException("Flow with name '" + request.getName() + "' already exists");
        }

        Flow flow = Flow.builder()
            .name(request.getName())
            .description(request.getDescription())
            .createdBy(userId)
            .build();

        flow = flowRepository.save(flow);
        log.info("Flow created: id={}, name={}", flow.getId(), flow.getName());

        return FlowResponse.from(flow);
    }

    @Transactional
    public FlowResponse updateFlow(UUID id, UpdateFlowRequest request) {
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + id));

        if (request.getName() != null && !request.getName().equals(flow.getName())) {
            if (flowRepository.existsByNameAndCreatedByAndIsDeletedFalse(request.getName(), flow.getCreatedBy())) {
                throw new IllegalArgumentException("Flow with name '" + request.getName() + "' already exists");
            }
            flow.setName(request.getName());
        }

        if (request.getDescription() != null) {
            flow.setDescription(request.getDescription());
        }

        flow = flowRepository.save(flow);
        return FlowResponse.from(flow);
    }

    @Transactional
    public void deleteFlow(UUID id) {
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + id));

        // Clean up related data
        flowShareRepository.deleteByFlowId(id);

        flow.setIsDeleted(true);
        flowRepository.save(flow);
        log.info("Flow deleted: id={}, shares cleaned up", id);
    }

    @Transactional
    public FlowResponse cloneFlow(UUID flowId, String newName, UUID userId) {
        Flow original = flowRepository.findByIdAndIsDeletedFalse(flowId)
            .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + flowId));

        String clonedName = newName != null ? newName : original.getName() + " (Copy)";

        // Ensure unique name (with safety limit)
        int counter = 1;
        String baseName = clonedName;
        while (flowRepository.existsByNameAndCreatedByAndIsDeletedFalse(clonedName, userId) && counter <= 100) {
            clonedName = baseName + " (" + counter + ")";
            counter++;
        }

        Flow cloned = Flow.builder()
            .name(clonedName)
            .description(original.getDescription())
            .createdBy(userId)
            .build();
        cloned = flowRepository.save(cloned);

        // Clone the latest version if exists
        List<FlowVersion> versions = flowVersionRepository.findByFlowIdOrderByCreatedAtDesc(flowId);
        if (!versions.isEmpty()) {
            FlowVersion latest = versions.get(0);
            FlowVersion clonedVersion = FlowVersion.builder()
                .flowId(cloned.getId())
                .version("1.0.0")
                .definition(latest.getDefinition() != null ? deepCopyMap(latest.getDefinition()) : null)
                .settings(latest.getSettings() != null ? deepCopyMap(latest.getSettings()) : Map.of())
                .status(Status.FlowVersion.DRAFT)
                .createdBy(userId)
                .build();
            flowVersionRepository.save(clonedVersion);
        }

        log.info("Flow cloned: originalId={}, newId={}, name={}", flowId, cloned.getId(), clonedName);
        return toFlowResponse(cloned);
    }

    // Version management

    public List<FlowVersionResponse> listVersions(UUID flowId) {
        if (!flowRepository.existsById(flowId)) {
            throw new ResourceNotFoundException("Flow not found: " + flowId);
        }

        return flowVersionRepository.findByFlowIdOrderByCreatedAtDesc(flowId)
            .stream()
            .map(FlowVersionResponse::from)
            .toList();
    }

    public FlowVersionResponse getVersion(UUID flowId, String version) {
        FlowVersion v = flowVersionRepository.findByFlowIdAndVersion(flowId, version)
            .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + version));
        return FlowVersionResponse.from(v);
    }

    public FlowVersionResponse getPublishedVersion(UUID flowId) {
        FlowVersion v = flowVersionRepository.findByFlowIdAndStatus(flowId, Status.FlowVersion.PUBLISHED)
            .orElseThrow(() -> new ResourceNotFoundException("No published version for flow: " + flowId));
        return FlowVersionResponse.from(v);
    }

    @Transactional
    public FlowVersionResponse saveVersion(UUID flowId, SaveVersionRequest request, UUID userId) {
        if (!flowRepository.existsById(flowId)) {
            throw new ResourceNotFoundException("Flow not found: " + flowId);
        }

        FlowVersion version = flowVersionRepository.findByFlowIdAndVersion(flowId, request.getVersion())
            .orElse(null);

        if (version != null) {
            // Update existing version (only if draft)
            if (!Status.FlowVersion.DRAFT.equals(version.getStatus())) {
                throw new IllegalArgumentException("Cannot modify a non-draft version");
            }
            version.setDefinition(request.getDefinition());
            if (request.getSettings() != null) {
                version.setSettings(request.getSettings());
            }
        } else {
            // Create new version
            version = FlowVersion.builder()
                .flowId(flowId)
                .version(request.getVersion())
                .definition(request.getDefinition())
                .settings(request.getSettings() != null ? request.getSettings() : Map.of())
                .status(Status.FlowVersion.DRAFT)
                .createdBy(userId)
                .build();
        }

        version = flowVersionRepository.save(version);
        log.info("Flow version saved: flowId={}, version={}", flowId, version.getVersion());

        return FlowVersionResponse.from(version);
    }

    @Transactional
    public FlowVersionResponse publishVersion(UUID flowId, String version) {
        FlowVersion v = flowVersionRepository.findByFlowIdAndVersion(flowId, version)
            .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + version));

        if (Status.FlowVersion.PUBLISHED.equals(v.getStatus())) {
            return FlowVersionResponse.from(v);
        }

        // Deprecate current published version
        flowVersionRepository.findByFlowIdAndStatus(flowId, Status.FlowVersion.PUBLISHED)
            .ifPresent(current -> {
                current.setStatus(Status.FlowVersion.DEPRECATED);
                flowVersionRepository.save(current);
            });

        v.setStatus(Status.FlowVersion.PUBLISHED);
        v = flowVersionRepository.save(v);
        log.info("Flow version published: flowId={}, version={}", flowId, version);

        return FlowVersionResponse.from(v);
    }

    public FlowValidationResponse validateFlow(UUID flowId, String version) {
        FlowVersion v = flowVersionRepository.findByFlowIdAndVersion(flowId, version)
            .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + version));

        DagParser.ParseResult result = dagParser.parse(v.getDefinition());

        return FlowValidationResponse.builder()
            .valid(result.isValid())
            .errors(result.getErrors())
            .warnings(result.getWarnings())
            .entryPoints(result.getEntryPoints())
            .exitPoints(result.getExitPoints())
            .executionOrder(result.getExecutionOrder())
            .dependencies(result.getDependencies())
            .build();
    }

    public FlowValidationResponse validateDefinition(Map<String, Object> definition) {
        DagParser.ParseResult result = dagParser.parse(definition);

        return FlowValidationResponse.builder()
            .valid(result.isValid())
            .errors(result.getErrors())
            .warnings(result.getWarnings())
            .entryPoints(result.getEntryPoints())
            .exitPoints(result.getExitPoints())
            .executionOrder(result.getExecutionOrder())
            .dependencies(result.getDependencies())
            .build();
    }

    /**
     * Get upstream node outputs for a specific node in a flow version.
     * Used by the flow editor for input mapping.
     */
    @SuppressWarnings("unchecked")
    public List<UpstreamNodeOutput> getUpstreamOutputs(UUID flowId, String version, String nodeId) {
        FlowVersion v = flowVersionRepository.findByFlowIdAndVersion(flowId, version)
            .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + version));

        Map<String, Object> definition = v.getDefinition();
        if (definition == null) {
            return List.of();
        }

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) definition.get("edges");

        if (nodes == null || edges == null) {
            return List.of();
        }

        // Build a map of nodeId -> node data
        Map<String, Map<String, Object>> nodeMap = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            String id = (String) node.get("id");
            if (id != null) {
                nodeMap.put(id, node);
            }
        }

        // Find all upstream nodes (source nodes of edges targeting our nodeId)
        Set<String> upstreamNodeIds = new HashSet<>();
        for (Map<String, Object> edge : edges) {
            String target = (String) edge.get("target");
            String source = (String) edge.get("source");
            if (nodeId.equals(target) && source != null) {
                upstreamNodeIds.add(source);
            }
        }

        // Build UpstreamNodeOutput for each upstream node
        List<UpstreamNodeOutput> result = new ArrayList<>();
        for (String upstreamNodeId : upstreamNodeIds) {
            Map<String, Object> nodeData = nodeMap.get(upstreamNodeId);
            if (nodeData == null) {
                continue;
            }

            Map<String, Object> data = (Map<String, Object>) nodeData.get("data");
            if (data == null) {
                data = Map.of();
            }

            String nodeLabel = (String) data.getOrDefault("label", upstreamNodeId);
            String nodeType = (String) data.getOrDefault("nodeType", "action");

            // Get output schema from handler or external service
            Map<String, Object> outputSchema = getNodeOutputSchema(nodeType, data);
            List<UpstreamNodeOutput.OutputField> flattenedFields = flattenOutputSchema(outputSchema, upstreamNodeId);

            result.add(UpstreamNodeOutput.builder()
                .nodeId(upstreamNodeId)
                .nodeLabel(nodeLabel)
                .nodeType(nodeType)
                .outputSchema(outputSchema)
                .flattenedFields(flattenedFields)
                .build());
        }

        return result;
    }

    /**
     * Get output schema for a node based on its type.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getNodeOutputSchema(String nodeType, Map<String, Object> nodeData) {
        // For external service nodes, fetch schema from service
        if ("externalService".equals(nodeType)) {
            String serviceIdStr = (String) nodeData.get("serviceId");
            String endpointIdStr = (String) nodeData.get("endpointId");
            if (serviceIdStr != null && endpointIdStr != null) {
                try {
                    UUID serviceId = UUID.fromString(serviceIdStr);
                    UUID endpointId = UUID.fromString(endpointIdStr);
                    EndpointSchemaResponse schema = externalServiceService.getEndpointSchema(serviceId, endpointId);
                    Map<String, Object> interfaceDef = schema.getInterfaceDefinition();
                    if (interfaceDef != null && interfaceDef.containsKey("outputs")) {
                        List<Map<String, Object>> outputs = (List<Map<String, Object>>) interfaceDef.get("outputs");
                        if (!outputs.isEmpty() && outputs.get(0).containsKey("schema")) {
                            return (Map<String, Object>) outputs.get(0).get("schema");
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not fetch external service schema: {}", e.getMessage());
                }
            }
        }

        // For other node types, get schema from handler
        Optional<NodeHandler> handlerOpt = nodeHandlerRegistry.findHandler(nodeType);
        if (handlerOpt.isPresent()) {
            NodeHandler handler = handlerOpt.get();
            Map<String, Object> interfaceDef = handler.getInterfaceDefinition();
            if (interfaceDef != null && interfaceDef.containsKey("outputs")) {
                List<Map<String, Object>> outputs = (List<Map<String, Object>>) interfaceDef.get("outputs");
                if (!outputs.isEmpty()) {
                    // Return a combined schema for all outputs
                    Map<String, Object> schema = new LinkedHashMap<>();
                    schema.put("type", "object");
                    Map<String, Object> properties = new LinkedHashMap<>();
                    for (Map<String, Object> output : outputs) {
                        String name = (String) output.get("name");
                        String type = (String) output.getOrDefault("type", "any");
                        String description = (String) output.get("description");
                        Map<String, Object> prop = new LinkedHashMap<>();
                        prop.put("type", type);
                        if (description != null) {
                            prop.put("description", description);
                        }
                        properties.put(name, prop);
                    }
                    schema.put("properties", properties);
                    return schema;
                }
            }
        }

        // Default output schema
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "data", Map.of("type", "any", "description", "Node output data")
            )
        );
    }

    /**
     * Flatten output schema into a list of selectable fields.
     */
    @SuppressWarnings("unchecked")
    private List<UpstreamNodeOutput.OutputField> flattenOutputSchema(Map<String, Object> schema, String nodeId) {
        List<UpstreamNodeOutput.OutputField> fields = new ArrayList<>();
        flattenSchemaRecursive(schema, "", nodeId, fields, 0);
        return fields;
    }

    @SuppressWarnings("unchecked")
    private void flattenSchemaRecursive(
            Map<String, Object> schema,
            String basePath,
            String nodeId,
            List<UpstreamNodeOutput.OutputField> fields,
            int depth) {

        if (depth > 5) {
            return; // Prevent infinite recursion
        }

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> propSchema = (Map<String, Object>) entry.getValue();

            String path = basePath.isEmpty() ? key : basePath + "." + key;
            String type = (String) propSchema.getOrDefault("type", "any");
            String description = (String) propSchema.get("description");
            String expression = "{{ $node[\"" + nodeId + "\"].json." + path + " }}";

            fields.add(UpstreamNodeOutput.OutputField.builder()
                .path(path)
                .type(type)
                .description(description)
                .expression(expression)
                .build());

            // Recurse into nested objects
            if ("object".equals(type) && propSchema.containsKey("properties")) {
                flattenSchemaRecursive(propSchema, path, nodeId, fields, depth + 1);
            }

            // Handle arrays
            if ("array".equals(type) && propSchema.containsKey("items")) {
                Map<String, Object> itemsSchema = (Map<String, Object>) propSchema.get("items");
                if (itemsSchema != null && "object".equals(itemsSchema.get("type"))) {
                    flattenSchemaRecursive(itemsSchema, path + "[0]", nodeId, fields, depth + 1);
                }
            }
        }
    }

    // ========== Data Pinning Methods ==========

    /**
     * Get all pinned data for a flow version.
     */
    public Map<String, Object> getPinnedData(UUID flowId, String version) {
        FlowVersion v = flowVersionRepository.findByFlowIdAndVersion(flowId, version)
            .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + version));
        return v.getPinnedData() != null ? v.getPinnedData() : Map.of();
    }

    /**
     * Pin data to a specific node.
     */
    @Transactional
    public void pinNodeData(UUID flowId, String version, PinDataRequest request) {
        FlowVersion v = flowVersionRepository.findByFlowIdAndVersion(flowId, version)
            .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + version));

        Map<String, Object> pinnedData = v.getPinnedData();
        if (pinnedData == null || pinnedData.isEmpty()) {
            pinnedData = new HashMap<>();
        } else {
            // Create a mutable copy if the map is immutable
            pinnedData = new HashMap<>(pinnedData);
        }

        pinnedData.put(request.getNodeId(), request.getData());
        v.setPinnedData(pinnedData);
        flowVersionRepository.save(v);

        log.info("Data pinned to node: flowId={}, version={}, nodeId={}",
            flowId, version, request.getNodeId());
    }

    /**
     * Unpin data from a specific node.
     */
    @Transactional
    public void unpinNodeData(UUID flowId, String version, String nodeId) {
        FlowVersion v = flowVersionRepository.findByFlowIdAndVersion(flowId, version)
            .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + version));

        Map<String, Object> pinnedData = v.getPinnedData();
        if (pinnedData == null || pinnedData.isEmpty()) {
            return; // Nothing to unpin
        }

        // Create a mutable copy
        pinnedData = new HashMap<>(pinnedData);
        pinnedData.remove(nodeId);
        v.setPinnedData(pinnedData);
        flowVersionRepository.save(v);

        log.info("Data unpinned from node: flowId={}, version={}, nodeId={}",
            flowId, version, nodeId);
    }
}
