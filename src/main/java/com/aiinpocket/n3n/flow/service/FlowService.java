package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.common.constant.Status;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlowService {

    private final FlowRepository flowRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final DagParser dagParser;

    public Page<FlowResponse> listFlows(Pageable pageable) {
        Page<Flow> flowPage = flowRepository.findByIsDeletedFalse(pageable);
        return toFlowResponsePage(flowPage);
    }

    public Page<FlowResponse> searchFlows(String query, Pageable pageable) {
        if (query == null || query.trim().isEmpty()) {
            return listFlows(pageable);
        }
        Page<Flow> flowPage = flowRepository.searchFlows(query.trim(), pageable);
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
}
