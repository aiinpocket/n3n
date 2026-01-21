package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.flow.dto.*;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlowService {

    private final FlowRepository flowRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final DagParser dagParser;

    public Page<FlowResponse> listFlows(Pageable pageable) {
        return flowRepository.findByIsDeletedFalse(pageable)
            .map(this::toFlowResponse);
    }

    public Page<FlowResponse> searchFlows(String query, Pageable pageable) {
        if (query == null || query.trim().isEmpty()) {
            return listFlows(pageable);
        }
        return flowRepository.searchFlows(query.trim(), pageable)
            .map(this::toFlowResponse);
    }

    private FlowResponse toFlowResponse(Flow flow) {
        List<FlowVersion> versions = flowVersionRepository.findByFlowIdOrderByCreatedAtDesc(flow.getId());
        String latestVersion = versions.isEmpty() ? null : versions.get(0).getVersion();
        String publishedVersion = versions.stream()
            .filter(v -> "published".equals(v.getStatus()))
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
            .filter(v -> "published".equals(v.getStatus()))
            .findFirst()
            .map(FlowVersion::getVersion)
            .orElse(null);

        return FlowResponse.from(flow, latestVersion, publishedVersion);
    }

    @Transactional
    public FlowResponse createFlow(CreateFlowRequest request, UUID userId) {
        if (flowRepository.existsByNameAndIsDeletedFalse(request.getName())) {
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
            if (flowRepository.existsByNameAndIsDeletedFalse(request.getName())) {
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

        flow.setIsDeleted(true);
        flowRepository.save(flow);
        log.info("Flow deleted: id={}", id);
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
        FlowVersion v = flowVersionRepository.findByFlowIdAndStatus(flowId, "published")
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
            if (!"draft".equals(version.getStatus())) {
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
                .status("draft")
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

        if ("published".equals(v.getStatus())) {
            return FlowVersionResponse.from(v);
        }

        // Deprecate current published version
        flowVersionRepository.findByFlowIdAndStatus(flowId, "published")
            .ifPresent(current -> {
                current.setStatus("deprecated");
                flowVersionRepository.save(current);
            });

        v.setStatus("published");
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
}
