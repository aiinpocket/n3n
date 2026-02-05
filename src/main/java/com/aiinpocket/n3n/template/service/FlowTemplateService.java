package com.aiinpocket.n3n.template.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.flow.dto.FlowResponse;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.template.dto.CreateTemplateRequest;
import com.aiinpocket.n3n.template.dto.OfficialTemplateDto;
import com.aiinpocket.n3n.template.dto.TemplateResponse;
import com.aiinpocket.n3n.template.entity.FlowTemplate;
import com.aiinpocket.n3n.template.repository.FlowTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlowTemplateService {

    private final FlowTemplateRepository templateRepository;
    private final FlowRepository flowRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final ObjectMapper objectMapper;

    // Official templates loaded from JSON
    private List<OfficialTemplateDto> officialTemplates = new ArrayList<>();
    private List<OfficialTemplateDto.CategoryDto> officialCategories = new ArrayList<>();

    @PostConstruct
    public void loadOfficialTemplates() {
        try {
            ClassPathResource resource = new ClassPathResource("templates/official-templates.json");
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);

                // Load templates
                JsonNode templatesNode = root.get("templates");
                if (templatesNode != null && templatesNode.isArray()) {
                    officialTemplates = new ArrayList<>();
                    for (JsonNode node : templatesNode) {
                        officialTemplates.add(parseOfficialTemplate(node));
                    }
                }

                // Load categories
                JsonNode categoriesNode = root.get("categories");
                if (categoriesNode != null && categoriesNode.isArray()) {
                    officialCategories = new ArrayList<>();
                    for (JsonNode node : categoriesNode) {
                        officialCategories.add(parseOfficialCategory(node));
                    }
                }

                log.info("Loaded {} official templates in {} categories",
                        officialTemplates.size(), officialCategories.size());
            }
        } catch (IOException e) {
            log.error("Failed to load official templates", e);
        }
    }

    // ==================== Official Templates API ====================

    /**
     * Get all official templates
     */
    public List<OfficialTemplateDto> getOfficialTemplates() {
        return Collections.unmodifiableList(officialTemplates);
    }

    /**
     * Get official template categories
     */
    public List<OfficialTemplateDto.CategoryDto> getOfficialCategories() {
        return Collections.unmodifiableList(officialCategories);
    }

    /**
     * Get official templates by category
     */
    public List<OfficialTemplateDto> getOfficialTemplatesByCategory(String category) {
        return officialTemplates.stream()
                .filter(t -> category.equals(t.getCategory()))
                .toList();
    }

    /**
     * Get official template by ID
     */
    public Optional<OfficialTemplateDto> getOfficialTemplateById(String templateId) {
        return officialTemplates.stream()
                .filter(t -> templateId.equals(t.getId()))
                .findFirst();
    }

    /**
     * Search official templates
     */
    public List<OfficialTemplateDto> searchOfficialTemplates(String query) {
        if (query == null || query.isBlank()) {
            return officialTemplates;
        }

        String lowerQuery = query.toLowerCase();
        return officialTemplates.stream()
                .filter(t -> matchesOfficialQuery(t, lowerQuery))
                .sorted((a, b) -> calculateOfficialRelevance(b, lowerQuery) - calculateOfficialRelevance(a, lowerQuery))
                .toList();
    }

    /**
     * Get recommended templates based on user input
     */
    public List<OfficialTemplateDto> getRecommendedTemplates(String userInput, int limit) {
        if (userInput == null || userInput.isBlank()) {
            return officialTemplates.stream()
                    .limit(limit)
                    .toList();
        }

        String lowerInput = userInput.toLowerCase();
        Set<String> keywords = extractKeywords(lowerInput);

        return officialTemplates.stream()
                .map(t -> new ScoredTemplate(t, calculateMatchScore(t, lowerInput, keywords)))
                .filter(st -> st.score > 0)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(limit)
                .map(st -> st.template)
                .toList();
    }

    // Helper methods for official templates
    private OfficialTemplateDto parseOfficialTemplate(JsonNode node) {
        OfficialTemplateDto template = new OfficialTemplateDto();
        template.setId(node.get("id").asText());
        template.setName(node.get("name").asText());
        template.setDescription(node.get("description").asText());
        template.setCategory(node.get("category").asText());

        List<String> tags = new ArrayList<>();
        JsonNode tagsNode = node.get("tags");
        if (tagsNode != null && tagsNode.isArray()) {
            for (JsonNode t : tagsNode) {
                tags.add(t.asText());
            }
        }
        template.setTags(tags);

        template.setComplexity(node.has("complexity") ? node.get("complexity").asText() : "medium");
        template.setEstimatedNodes(node.has("estimatedNodes") ? node.get("estimatedNodes").asInt() : 0);

        List<String> useCases = new ArrayList<>();
        JsonNode useCasesNode = node.get("useCases");
        if (useCasesNode != null && useCasesNode.isArray()) {
            for (JsonNode uc : useCasesNode) {
                useCases.add(uc.asText());
            }
        }
        template.setUseCases(useCases);

        JsonNode definitionNode = node.get("definition");
        if (definitionNode != null) {
            template.setDefinition(objectMapper.convertValue(definitionNode, Map.class));
        }

        return template;
    }

    private OfficialTemplateDto.CategoryDto parseOfficialCategory(JsonNode node) {
        OfficialTemplateDto.CategoryDto category = new OfficialTemplateDto.CategoryDto();
        category.setId(node.get("id").asText());
        category.setName(node.get("name").asText());
        category.setDescription(node.has("description") ? node.get("description").asText() : "");
        category.setIcon(node.has("icon") ? node.get("icon").asText() : "folder");
        return category;
    }

    private boolean matchesOfficialQuery(OfficialTemplateDto template, String query) {
        return template.getName().toLowerCase().contains(query)
                || template.getDescription().toLowerCase().contains(query)
                || template.getTags().stream().anyMatch(t -> t.toLowerCase().contains(query))
                || template.getUseCases().stream().anyMatch(uc -> uc.toLowerCase().contains(query));
    }

    private int calculateOfficialRelevance(OfficialTemplateDto template, String query) {
        int score = 0;
        if (template.getName().toLowerCase().contains(query)) score += 10;
        if (template.getDescription().toLowerCase().contains(query)) score += 5;
        for (String tag : template.getTags()) {
            if (tag.toLowerCase().contains(query)) score += 3;
        }
        return score;
    }

    private Set<String> extractKeywords(String input) {
        Set<String> keywords = new HashSet<>();
        Map<String, List<String>> keywordMappings = Map.ofEntries(
                Map.entry("每天", Arrays.asList("schedule", "daily")),
                Map.entry("定時", Arrays.asList("schedule", "cron")),
                Map.entry("郵件", Arrays.asList("email", "mail")),
                Map.entry("通知", Arrays.asList("notification", "alert", "notify")),
                Map.entry("slack", Arrays.asList("slack", "messaging")),
                Map.entry("telegram", Arrays.asList("telegram", "messaging")),
                Map.entry("資料庫", Arrays.asList("database", "sql")),
                Map.entry("api", Arrays.asList("api", "http", "webhook")),
                Map.entry("ai", Arrays.asList("ai", "openai", "chatgpt")),
                Map.entry("翻譯", Arrays.asList("translation", "translate")),
                Map.entry("摘要", Arrays.asList("summary", "summarize")),
                Map.entry("監控", Arrays.asList("monitoring", "alert")),
                Map.entry("審批", Arrays.asList("approval", "workflow")),
                Map.entry("github", Arrays.asList("github", "git", "cicd")),
                Map.entry("支付", Arrays.asList("payment", "stripe"))
        );

        for (Map.Entry<String, List<String>> entry : keywordMappings.entrySet()) {
            if (input.contains(entry.getKey())) {
                keywords.addAll(entry.getValue());
            }
        }

        String[] words = input.split("\\s+");
        for (String word : words) {
            if (word.matches("[a-zA-Z]+") && word.length() > 2) {
                keywords.add(word.toLowerCase());
            }
        }

        return keywords;
    }

    private double calculateMatchScore(OfficialTemplateDto template, String input, Set<String> keywords) {
        double score = 0;
        if (template.getName().toLowerCase().contains(input)) score += 20;
        if (template.getDescription().toLowerCase().contains(input)) score += 10;
        for (String tag : template.getTags()) {
            if (keywords.contains(tag.toLowerCase())) score += 5;
            if (input.contains(tag.toLowerCase())) score += 3;
        }
        for (String useCase : template.getUseCases()) {
            if (input.contains(useCase.toLowerCase())) score += 5;
        }
        String category = template.getCategory().toLowerCase();
        if (keywords.stream().anyMatch(k -> category.contains(k))) score += 3;
        return score;
    }

    private static class ScoredTemplate {
        OfficialTemplateDto template;
        double score;
        ScoredTemplate(OfficialTemplateDto template, double score) {
            this.template = template;
            this.score = score;
        }
    }

    // ==================== User Templates API ====================

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
