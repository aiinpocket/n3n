package com.aiinpocket.n3n.template.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.flow.dto.FlowResponse;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.template.dto.CreateTemplateRequest;
import com.aiinpocket.n3n.template.dto.OfficialTemplateDto;
import com.aiinpocket.n3n.template.dto.TemplateResponse;
import com.aiinpocket.n3n.template.dto.UpdateTemplateRequest;
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
    private final NodeHandlerRegistry nodeHandlerRegistry;

    // Official templates loaded from JSON (volatile for thread-safe publication from @PostConstruct)
    private volatile List<OfficialTemplateDto> officialTemplates = List.of();
    private volatile List<OfficialTemplateDto.CategoryDto> officialCategories = List.of();

    @PostConstruct
    public void loadOfficialTemplates() {
        try {
            ClassPathResource resource = new ClassPathResource("templates/official-templates.json");
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);

                // Load templates
                JsonNode templatesNode = root.get("templates");
                if (templatesNode != null && templatesNode.isArray()) {
                    List<OfficialTemplateDto> templates = new ArrayList<>();
                    for (JsonNode node : templatesNode) {
                        templates.add(parseOfficialTemplate(node));
                    }
                    officialTemplates = List.copyOf(templates);
                }

                // Load categories
                JsonNode categoriesNode = root.get("categories");
                if (categoriesNode != null && categoriesNode.isArray()) {
                    List<OfficialTemplateDto.CategoryDto> categories = new ArrayList<>();
                    for (JsonNode node : categoriesNode) {
                        categories.add(parseOfficialCategory(node));
                    }
                    officialCategories = List.copyOf(categories);
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
     * Get all official templates（只含本站台真的裝得起來的，見 {@link #isUsable}）
     */
    public List<OfficialTemplateDto> getOfficialTemplates() {
        return officialTemplates.stream()
                .filter(this::isUsable)
                .toList();
    }

    /**
     * Get official template categories（只留下還有可用範本的分類，避免使用者點進空分類）
     */
    public List<OfficialTemplateDto.CategoryDto> getOfficialCategories() {
        Set<String> usedCategories = getOfficialTemplates().stream()
                .map(OfficialTemplateDto::getCategory)
                .collect(java.util.stream.Collectors.toSet());
        return officialCategories.stream()
                .filter(c -> usedCategories.contains(c.getId()))
                .toList();
    }

    /**
     * Get official templates by category
     */
    public List<OfficialTemplateDto> getOfficialTemplatesByCategory(String category) {
        return officialTemplates.stream()
                .filter(t -> category.equals(t.getCategory()))
                .filter(this::isUsable)
                .toList();
    }

    /**
     * 範本裡若用到本站台沒安裝的節點，套用後流程是壞的、使用者也看不懂為什麼，
     * 所以直接不列出來——寧可少幾個選項，也不要給死路。
     */
    @SuppressWarnings("unchecked")
    private boolean isUsable(OfficialTemplateDto template) {
        Map<String, Object> definition = template.getDefinition();
        if (definition == null || !(definition.get("nodes") instanceof List<?> nodes)) {
            return false;
        }
        return ((List<Map<String, Object>>) nodes).stream()
                .map(n -> String.valueOf(n.get("type")))
                .allMatch(nodeHandlerRegistry::hasHandler);
    }

    /**
     * Get official template by ID
     */
    public Optional<OfficialTemplateDto> getOfficialTemplateById(String templateId) {
        return officialTemplates.stream()
                .filter(t -> templateId.equals(t.getId()))
                .filter(this::isUsable)
                .findFirst();
    }

    /**
     * Search official templates
     */
    public List<OfficialTemplateDto> searchOfficialTemplates(String query) {
        if (query == null || query.isBlank()) {
            return getOfficialTemplates();
        }

        String lowerQuery = query.toLowerCase();
        return officialTemplates.stream()
                .filter(this::isUsable)
                .filter(t -> matchesOfficialQuery(t, lowerQuery))
                .sorted((a, b) -> calculateOfficialRelevance(b, lowerQuery) - calculateOfficialRelevance(a, lowerQuery))
                .toList();
    }

    /**
     * Get recommended templates based on user input
     */
    public List<OfficialTemplateDto> getRecommendedTemplates(String userInput, int limit) {
        if (userInput == null || userInput.isBlank()) {
            return getOfficialTemplates().stream()
                    .limit(limit)
                    .toList();
        }

        String lowerInput = userInput.toLowerCase();
        Set<String> keywords = extractKeywords(lowerInput);

        return officialTemplates.stream()
                .filter(this::isUsable)
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
        template.setId(node.path("id").asText(""));
        template.setName(node.path("name").asText(""));
        template.setDescription(node.path("description").asText(""));
        template.setCategory(node.path("category").asText(""));

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
        category.setId(node.path("id").asText(""));
        category.setName(node.path("name").asText(""));
        category.setDescription(node.path("description").asText(""));
        category.setIcon(node.path("icon").asText("folder"));
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

    @Transactional(readOnly = true)
    public Page<TemplateResponse> listTemplates(Pageable pageable) {
        return templateRepository.findAllByOrderByUsageCountDesc(pageable)
            .map(TemplateResponse::summary);
    }

    @Transactional(readOnly = true)
    public Page<TemplateResponse> listTemplatesByCategory(String category, Pageable pageable) {
        return templateRepository.findByCategoryOrderByUsageCountDesc(category, pageable)
            .map(TemplateResponse::summary);
    }

    @Transactional(readOnly = true)
    public Page<TemplateResponse> searchTemplates(String query, Pageable pageable) {
        if (query == null || query.trim().isEmpty()) {
            return listTemplates(pageable);
        }
        return templateRepository.searchTemplates(query.trim(), pageable)
            .map(TemplateResponse::summary);
    }

    @Transactional(readOnly = true)
    public List<String> listCategories() {
        return templateRepository.findAllCategories();
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listMyTemplates(UUID userId) {
        return templateRepository.findByCreatedByOrderByCreatedAtDesc(userId)
            .stream()
            .map(TemplateResponse::summary)
            .toList();
    }

    @Transactional(readOnly = true)
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
    public TemplateResponse updateTemplate(UUID id, UpdateTemplateRequest request, UUID userId) {
        FlowTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + id));

        if (!template.getCreatedBy().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        if (request.getName() != null) {
            template.setName(request.getName());
        }
        if (request.getDescription() != null) {
            template.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            template.setCategory(request.getCategory());
        }
        if (request.getTags() != null) {
            template.setTags(request.getTags());
        }

        template = templateRepository.save(template);
        log.info("Template updated: id={}, name={}", id, template.getName());
        return TemplateResponse.from(template);
    }

    @Transactional
    public TemplateResponse createTemplateFromFlow(UUID flowId, String version, CreateTemplateRequest request, UUID userId) {
        // Verify flow ownership before allowing template creation
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(flowId)
            .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + flowId));
        if (!flow.getCreatedBy().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        FlowVersion flowVersion = flowVersionRepository.findByFlowIdAndVersion(flowId, version)
            .orElseThrow(() -> new ResourceNotFoundException("Flow version not found: " + flowId + "/" + version));

        // Strip credential references to prevent leaking user credentials in shared templates
        Map<String, Object> sanitizedDefinition = removeCredentialReferences(flowVersion.getDefinition());

        FlowTemplate template = FlowTemplate.builder()
            .name(request.getName())
            .description(request.getDescription())
            .category(request.getCategory())
            .tags(request.getTags())
            .definition(sanitizedDefinition)
            .thumbnailUrl(request.getThumbnailUrl())
            .createdBy(userId)
            .build();

        template = templateRepository.save(template);
        log.info("Template created from flow: id={}, flowId={}, version={}", template.getId(), flowId, version);

        return TemplateResponse.from(template);
    }

    /**
     * 由內建的官方範本建立流程。
     * 官方範本 JSON 用的是精簡格式（node 只有 id/type/label/config，edge 只有 source/target），
     * 這裡轉成流程編輯器實際持久化的格式，否則使用者開啟後畫布是空的。
     */
    @Transactional
    public FlowResponse createFlowFromOfficialTemplate(String templateId, String flowName, UUID userId) {
        OfficialTemplateDto template = getOfficialTemplateById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("Official template not found: " + templateId));

        Flow flow = Flow.builder()
            .name(flowName)
            .description(template.getDescription())
            .createdBy(userId)
            .build();
        flow = flowRepository.save(flow);

        FlowVersion version = FlowVersion.builder()
            .flowId(flow.getId())
            .version("1.0.0")
            .definition(toEditorDefinition(template.getDefinition()))
            .settings(Map.of())
            .status("draft")
            .createdBy(userId)
            .build();
        flowVersionRepository.save(version);

        log.info("Flow created from official template: flowId={}, templateId={}", flow.getId(), templateId);

        return FlowResponse.from(flow, "1.0.0", null);
    }

    /**
     * 把官方範本的精簡定義轉成編輯器格式：
     * node 補上 position 與 data（label + nodeType + config），edge 補上 id 與 edgeType。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toEditorDefinition(Map<String, Object> definition) {
        if (definition == null) {
            return Map.of("nodes", List.of(), "edges", List.of());
        }

        List<Map<String, Object>> rawNodes = definition.get("nodes") instanceof List<?> list
            ? (List<Map<String, Object>>) list
            : List.of();
        List<Map<String, Object>> rawEdges = definition.get("edges") instanceof List<?> list
            ? (List<Map<String, Object>>) list
            : List.of();

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (int i = 0; i < rawNodes.size(); i++) {
            Map<String, Object> raw = rawNodes.get(i);
            String nodeType = String.valueOf(raw.getOrDefault("type", ""));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("label", raw.getOrDefault("label", nodeType));
            data.put("nodeType", nodeType);
            if (raw.get("config") instanceof Map<?, ?> config) {
                data.putAll((Map<String, Object>) config);
            }

            nodes.add(Map.of(
                "id", raw.getOrDefault("id", "n" + (i + 1)),
                "type", nodeType,
                "position", Map.of("x", 250, "y", i * 120 + 50),
                "data", data
            ));
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (int i = 0; i < rawEdges.size(); i++) {
            Map<String, Object> raw = rawEdges.get(i);
            edges.add(Map.of(
                "id", raw.getOrDefault("id", "edge-" + i),
                "source", raw.getOrDefault("source", ""),
                "target", raw.getOrDefault("target", ""),
                "edgeType", raw.getOrDefault("edgeType", "success")
            ));
        }

        return Map.of("nodes", List.copyOf(nodes), "edges", List.copyOf(edges));
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

        // Create initial version with template definition (strip credential refs as defense-in-depth)
        Map<String, Object> cleanDefinition = removeCredentialReferences(template.getDefinition());
        FlowVersion version = FlowVersion.builder()
            .flowId(flow.getId())
            .version("1.0.0")
            .definition(cleanDefinition)
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
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        templateRepository.delete(template);
        log.info("Template deleted: id={}", id);
    }

    /**
     * Remove credential references from flow definition to prevent leaking user credentials.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> removeCredentialReferences(Map<String, Object> definition) {
        if (definition == null) {
            return Map.of();
        }

        Map<String, Object> newDefinition = new HashMap<>(definition);

        Object nodesObj = definition.get("nodes");
        if (nodesObj instanceof List<?> nodesList) {
            List<Object> nodes = new ArrayList<>();

            for (Object nodeObj : nodesList) {
                if (nodeObj instanceof Map<?, ?> nodeMap) {
                    Map<String, Object> newNode = new HashMap<>((Map<String, Object>) nodeMap);
                    Object rawData = nodeMap.get("data");
                    if (rawData instanceof Map<?, ?> dataMap) {
                        Map<String, Object> data = new HashMap<>((Map<String, Object>) dataMap);
                        data.remove("credentialId");
                        newNode.put("data", data);
                    }
                    nodes.add(newNode);
                } else {
                    nodes.add(nodeObj);
                }
            }

            newDefinition.put("nodes", nodes);
        }

        return newDefinition;
    }
}
