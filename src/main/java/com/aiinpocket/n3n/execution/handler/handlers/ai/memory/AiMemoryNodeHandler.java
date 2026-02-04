package com.aiinpocket.n3n.execution.handler.handlers.ai.memory;

import com.aiinpocket.n3n.ai.provider.AiProviderFactory;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.handlers.ai.base.AbstractAiNodeHandler;
import com.aiinpocket.n3n.execution.handler.handlers.ai.base.StreamChunk;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * AI Memory 節點處理器
 *
 * 功能：
 * - 對話記憶存取
 * - 會話管理
 * - 記憶摘要生成
 * - 支援多種儲存後端
 */
@Component
@Slf4j
public class AiMemoryNodeHandler extends AbstractAiNodeHandler {

    private final MemoryStore memoryStore;

    public AiMemoryNodeHandler(
        AiProviderFactory providerFactory,
        MemoryStore memoryStore
    ) {
        super(providerFactory);
        this.memoryStore = memoryStore;
    }

    @Override
    public String getType() {
        return "aiMemory";
    }

    @Override
    public String getDisplayName() {
        return "AI Memory";
    }

    @Override
    public String getDescription() {
        return "Manage conversation memory for AI sessions. Store, retrieve, and search conversation history.";
    }

    @Override
    public String getIcon() {
        return "database";
    }

    @Override
    public boolean supportsStreaming() {
        return false;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("memory", ResourceDef.of("memory", "Memory", "Conversation memory operations"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        operations.put("memory", List.of(
            // 儲存訊息
            OperationDef.create("store", "Store Message")
                .description("Store a message in conversation memory")
                .fields(List.of(
                    FieldDef.string("sessionId", "Session ID")
                        .withDescription("Unique session identifier")
                        .required(),
                    FieldDef.select("role", "Role", List.of("user", "assistant", "system"))
                        .withDefault("user")
                        .withDescription("Message role")
                        .required(),
                    FieldDef.textarea("content", "Content")
                        .withDescription("Message content to store")
                        .required()
                ))
                .outputDescription("Confirmation of stored message")
                .build(),

            // 取得歷史
            OperationDef.create("getHistory", "Get History")
                .description("Retrieve conversation history")
                .fields(List.of(
                    FieldDef.string("sessionId", "Session ID")
                        .withDescription("Unique session identifier")
                        .required(),
                    FieldDef.integer("limit", "Limit")
                        .withDefault(20)
                        .withRange(1, 100)
                        .withDescription("Maximum messages to retrieve")
                ))
                .outputDescription("Returns array of messages with role, content, and timestamp")
                .build(),

            // 清除記憶
            OperationDef.create("clear", "Clear Memory")
                .description("Clear all memory for a session")
                .fields(List.of(
                    FieldDef.string("sessionId", "Session ID")
                        .withDescription("Session to clear")
                        .required()
                ))
                .outputDescription("Confirmation of cleared memory")
                .build(),

            // 搜尋記憶
            OperationDef.create("search", "Search Memory")
                .description("Search conversation memory")
                .fields(List.of(
                    FieldDef.string("sessionId", "Session ID")
                        .withDescription("Session to search")
                        .required(),
                    FieldDef.string("query", "Query")
                        .withDescription("Search query")
                        .required(),
                    FieldDef.integer("limit", "Limit")
                        .withDefault(5)
                        .withRange(1, 20)
                ))
                .outputDescription("Returns matching messages")
                .build(),

            // 取得/設定摘要
            OperationDef.create("getSummary", "Get Summary")
                .description("Get session summary")
                .fields(List.of(
                    FieldDef.string("sessionId", "Session ID")
                        .required()
                ))
                .outputDescription("Returns session summary if exists")
                .build(),

            OperationDef.create("saveSummary", "Save Summary")
                .description("Save session summary")
                .fields(List.of(
                    FieldDef.string("sessionId", "Session ID")
                        .required(),
                    FieldDef.textarea("summary", "Summary")
                        .withDescription("Summary content")
                        .required()
                ))
                .outputDescription("Confirmation of saved summary")
                .build()
        ));

        return operations;
    }

    @Override
    public NodeExecutionResult executeOperation(
        NodeExecutionContext context,
        String resource,
        String operation,
        Map<String, Object> credential,
        Map<String, Object> params
    ) {
        if (!"memory".equals(resource)) {
            return NodeExecutionResult.failure("Unknown resource: " + resource);
        }

        try {
            return switch (operation) {
                case "store" -> executeStore(params);
                case "getHistory" -> executeGetHistory(params);
                case "clear" -> executeClear(params);
                case "search" -> executeSearch(params);
                case "getSummary" -> executeGetSummary(params);
                case "saveSummary" -> executeSaveSummary(params);
                default -> NodeExecutionResult.failure("Unknown operation: " + operation);
            };
        } catch (Exception e) {
            log.error("Memory operation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Memory operation failed: " + e.getMessage());
        }
    }

    private NodeExecutionResult executeStore(Map<String, Object> params) throws Exception {
        String sessionId = getRequiredParam(params, "sessionId");
        String role = getParam(params, "role", "user");
        String content = getRequiredParam(params, "content");

        MemoryStore.MemoryEntry entry = new MemoryStore.MemoryEntry(
            UUID.randomUUID().toString(),
            role,
            content,
            Map.of(),
            System.currentTimeMillis()
        );

        memoryStore.store(sessionId, entry).get();

        return NodeExecutionResult.success(Map.of(
            "stored", true,
            "entryId", entry.id(),
            "sessionId", sessionId
        ));
    }

    private NodeExecutionResult executeGetHistory(Map<String, Object> params) throws Exception {
        String sessionId = getRequiredParam(params, "sessionId");
        int limit = getIntParam(params, "limit", 20);

        List<MemoryStore.MemoryEntry> history = memoryStore.getHistory(sessionId, limit).get();

        List<Map<String, Object>> messages = history.stream()
            .map(e -> Map.<String, Object>of(
                "id", e.id(),
                "role", e.role(),
                "content", e.content(),
                "timestamp", e.timestamp()
            ))
            .toList();

        return NodeExecutionResult.success(Map.of(
            "messages", messages,
            "count", messages.size(),
            "sessionId", sessionId
        ));
    }

    private NodeExecutionResult executeClear(Map<String, Object> params) throws Exception {
        String sessionId = getRequiredParam(params, "sessionId");

        memoryStore.clear(sessionId).get();

        return NodeExecutionResult.success(Map.of(
            "cleared", true,
            "sessionId", sessionId
        ));
    }

    private NodeExecutionResult executeSearch(Map<String, Object> params) throws Exception {
        String sessionId = getRequiredParam(params, "sessionId");
        String query = getRequiredParam(params, "query");
        int limit = getIntParam(params, "limit", 5);

        List<MemoryStore.MemoryEntry> results = memoryStore.search(sessionId, query, limit).get();

        List<Map<String, Object>> messages = results.stream()
            .map(e -> Map.<String, Object>of(
                "id", e.id(),
                "role", e.role(),
                "content", e.content(),
                "timestamp", e.timestamp()
            ))
            .toList();

        return NodeExecutionResult.success(Map.of(
            "results", messages,
            "count", messages.size(),
            "query", query
        ));
    }

    private NodeExecutionResult executeGetSummary(Map<String, Object> params) throws Exception {
        String sessionId = getRequiredParam(params, "sessionId");

        Optional<String> summary = memoryStore.getSummary(sessionId).get();

        return NodeExecutionResult.success(Map.of(
            "sessionId", sessionId,
            "hasSummary", summary.isPresent(),
            "summary", summary.orElse("")
        ));
    }

    private NodeExecutionResult executeSaveSummary(Map<String, Object> params) throws Exception {
        String sessionId = getRequiredParam(params, "sessionId");
        String summary = getRequiredParam(params, "summary");

        memoryStore.saveSummary(sessionId, summary).get();

        return NodeExecutionResult.success(Map.of(
            "saved", true,
            "sessionId", sessionId
        ));
    }

    @Override
    public Flux<StreamChunk> executeStream(NodeExecutionContext context) {
        return Flux.just(StreamChunk.error("Memory node does not support streaming"));
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "sessionId", "type", "string", "required", true),
                Map.of("name", "content", "type", "string", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "messages", "type", "array"),
                Map.of("name", "summary", "type", "string")
            )
        );
    }
}
