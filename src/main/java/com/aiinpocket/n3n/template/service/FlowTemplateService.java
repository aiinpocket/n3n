package com.aiinpocket.n3n.template.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.flow.dto.FlowResponse;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.template.dto.CreateTemplateRequest;
import com.aiinpocket.n3n.template.dto.TemplateResponse;
import com.aiinpocket.n3n.template.entity.FlowTemplate;
import com.aiinpocket.n3n.template.repository.FlowTemplateRepository;
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
public class FlowTemplateService {

    private final FlowTemplateRepository templateRepository;
    private final FlowRepository flowRepository;
    private final FlowVersionRepository flowVersionRepository;

    public Page<TemplateResponse> listTemplates(Pageable pageable) {
        return templateRepository.findAllByOrderByUsageCountDesc(pageable)
            .map(TemplateResponse::summary);
    }

    public Page<TemplateResponse> listTemplatesByCategory(String category, Pageable pageable) {
        return templateRepository.findByCategoryOrderByUsageCountDesc(category, pageable)
            .map(TemplateResponse::summary);
    }

    public Page<TemplateResponse> searchTemplates(String query, Pageable pageable) {
        if (query == null || query.trim().isEmpty()) {
            return listTemplates(pageable);
        }
        return templateRepository.searchTemplates(query.trim(), pageable)
            .map(TemplateResponse::summary);
    }

    public List<String> listCategories() {
        return templateRepository.findAllCategories();
    }

    public List<TemplateResponse> listMyTemplates(UUID userId) {
        return templateRepository.findByCreatedByOrderByCreatedAtDesc(userId)
            .stream()
            .map(TemplateResponse::summary)
            .toList();
    }

    public TemplateResponse getTemplate(UUID id) {
        FlowTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + id));
        return TemplateResponse.from(template);
    }

    @Transactional
    public TemplateResponse createTemplate(CreateTemplateRequest request, UUID userId) {
        FlowTemplate template = FlowTemplate.builder()
            .name(request.getName())
            .description(request.getDescription())
            .category(request.getCategory())
            .tags(request.getTags())
            .definition(request.getDefinition())
            .thumbnailUrl(request.getThumbnailUrl())
            .createdBy(userId)
            .build();

        template = templateRepository.save(template);
        log.info("Template created: id={}, name={}", template.getId(), template.getName());

        return TemplateResponse.from(template);
    }

    @Transactional
    public TemplateResponse createTemplateFromFlow(UUID flowId, String version, CreateTemplateRequest request, UUID userId) {
        FlowVersion flowVersion = flowVersionRepository.findByFlowIdAndVersion(flowId, version)
            .orElseThrow(() -> new ResourceNotFoundException("Flow version not found: " + flowId + "/" + version));

        FlowTemplate template = FlowTemplate.builder()
            .name(request.getName())
            .description(request.getDescription())
            .category(request.getCategory())
            .tags(request.getTags())
            .definition(flowVersion.getDefinition())
            .thumbnailUrl(request.getThumbnailUrl())
            .createdBy(userId)
            .build();

        template = templateRepository.save(template);
        log.info("Template created from flow: id={}, flowId={}, version={}", template.getId(), flowId, version);

        return TemplateResponse.from(template);
    }

    @Transactional
    public FlowResponse createFlowFromTemplate(UUID templateId, String flowName, UUID userId) {
        FlowTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + templateId));

        // Create the flow
        Flow flow = Flow.builder()
            .name(flowName)
            .description("Created from template: " + template.getName())
            .createdBy(userId)
            .build();
        flow = flowRepository.save(flow);

        // Create initial version with template definition
        FlowVersion version = FlowVersion.builder()
            .flowId(flow.getId())
            .version("1.0.0")
            .definition(template.getDefinition())
            .settings(Map.of())
            .status("draft")
            .createdBy(userId)
            .build();
        flowVersionRepository.save(version);

        // Increment template use count
        templateRepository.incrementUsageCount(templateId);

        log.info("Flow created from template: flowId={}, templateId={}", flow.getId(), templateId);

        return FlowResponse.from(flow, "1.0.0", null);
    }

    @Transactional
    public void deleteTemplate(UUID id, UUID userId) {
        FlowTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + id));

        if (!template.getCreatedBy().equals(userId)) {
            throw new IllegalArgumentException("Cannot delete a template you didn't create");
        }

        templateRepository.delete(template);
        log.info("Template deleted: id={}", id);
    }
}
