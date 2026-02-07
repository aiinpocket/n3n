package com.aiinpocket.n3n.execution.handler.handlers.ai;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class AiRouterNodeHandlerTest {

    private AiRouterNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AiRouterNodeHandler();
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType() {
            assertThat(handler.getType()).isEqualTo("aiRouter");
        }

        @Test
        void getDisplayName() {
            assertThat(handler.getDisplayName()).contains("Router");
        }

        @Test
        void getCategory() {
            assertThat(handler.getCategory()).isEqualTo("AI");
        }

        @Test
        void getConfigSchema() {
            assertThat(handler.getConfigSchema()).containsKey("properties");
        }

        @Test
        void getInterfaceDefinition() {
            assertThat(handler.getInterfaceDefinition())
                    .containsKey("inputs")
                    .containsKey("outputs");
        }

        @Test
        void getDescription_isNotBlank() {
            assertThat(handler.getDescription()).isNotBlank();
        }

        @Test
        void getIcon_isNotBlank() {
            assertThat(handler.getIcon()).isNotBlank();
        }

        @Test
        void getConfigSchema_containsExpectedProperties() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("prompt");
            assertThat(properties).containsKey("routes");
            assertThat(properties).containsKey("defaultRoute");
        }
    }

    // ==================== Keyword-Based Routing ====================

    @Nested
    @DisplayName("Keyword-Based Routing")
    class KeywordRouting {

        @Test
        void selectsBestMatchByKeywordOverlap() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "billing", "description", "Handle billing invoices and payment questions"),
                    Map.of("name", "support", "description", "Handle technical support and troubleshooting"),
                    Map.of("name", "sales", "description", "Handle sales inquiries and pricing questions")
            );

            NodeExecutionContext context = buildContextWithRoutes(
                    "I need help with my billing invoice and payment", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("selectedRoute")).isEqualTo("billing");
        }

        @Test
        void routesDatabaseQueryCorrectly() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "email", "description", "Send and receive email messages"),
                    Map.of("name", "database", "description", "Query database tables and records"),
                    Map.of("name", "api", "description", "Make HTTP API calls and requests")
            );

            NodeExecutionContext context = buildContextWithRoutes(
                    "I need to query the database tables to find records", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("selectedRoute")).isEqualTo("database");
        }

        @Test
        void selectsRouteWithHigherKeywordOverlap() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "analytics", "description", "Analyze data and generate reports"),
                    Map.of("name", "reports", "description", "Generate detailed analytical reports from data analysis")
            );

            NodeExecutionContext context = buildContextWithRoutes(
                    "generate detailed analytical reports from data", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("selectedRoute")).isEqualTo("reports");
        }

        @Test
        void routesImageProcessingCorrectly() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "image-processing", "description", "Process images resize crop filter transform"),
                    Map.of("name", "text-processing", "description", "Process text documents parse extract analyze"),
                    Map.of("name", "video-processing", "description", "Process video files encode transcode stream")
            );

            NodeExecutionContext context = buildContextWithRoutes(
                    "I need to resize and crop this image with a filter transform", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("selectedRoute")).isEqualTo("image-processing");
        }

        @Test
        void selectsDeleteRouteOverCreateAndUpdate() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "create-user", "description", "Create new user accounts"),
                    Map.of("name", "delete-user", "description", "Delete existing user accounts"),
                    Map.of("name", "update-user", "description", "Update existing user account details")
            );

            NodeExecutionContext context = buildContextWithRoutes(
                    "I want to delete an existing user account", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("selectedRoute")).isEqualTo("delete-user");
        }

        @Test
        void singleRoute_alwaysSelected() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "process", "description", "Process data records")
            );

            NodeExecutionContext context = buildContextWithRoutes(
                    "I need to process some data records", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("selectedRoute")).isEqualTo("process");
        }

        @Test
        void confidenceScoreIsBetweenZeroAndOne() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "support", "description", "Handle technical support and troubleshooting")
            );

            NodeExecutionContext context = buildContextWithRoutes(
                    "I need technical support and troubleshooting help", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            double confidence = ((Number) result.getOutput().get("confidence")).doubleValue();
            assertThat(confidence).isBetween(0.0, 1.0);
        }

        @Test
        void outputContainsReasoningString() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "route1", "description", "Handle incoming requests")
            );

            NodeExecutionContext context = buildContextWithRoutes(
                    "handle incoming requests", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("reasoning")).isNotNull();
            assertThat(result.getOutput().get("reasoning").toString()).isNotBlank();
        }
    }

    // ==================== Output Structure ====================

    @Nested
    @DisplayName("Output Structure")
    class OutputStructure {

        @Test
        void outputHasAllExpectedKeys() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "route1", "description", "First route description")
            );

            NodeExecutionContext context = buildContextWithRoutes("first route test", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("selectedRoute");
            assertThat(result.getOutput()).containsKey("confidence");
            assertThat(result.getOutput()).containsKey("reasoning");
            assertThat(result.getOutput()).containsKey("input");
            assertThat(result.getOutput()).containsKey("allRoutes");
        }

        @Test
        void allRoutesListContainsRouteNames() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "alpha", "description", "Alpha route"),
                    Map.of("name", "beta", "description", "Beta route"),
                    Map.of("name", "gamma", "description", "Gamma route")
            );

            NodeExecutionContext context = buildContextWithRoutes("alpha route test", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> allRoutes = (List<String>) result.getOutput().get("allRoutes");
            assertThat(allRoutes).containsExactly("alpha", "beta", "gamma");
        }

        @Test
        void inputFieldReflectsOriginalPrompt() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "test", "description", "Test route")
            );

            NodeExecutionContext context = buildContextWithRoutes("my input prompt", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("input")).isEqualTo("my input prompt");
        }

        @Test
        void branchesToFollowMatchesSelectedRoute() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "branch-a", "description", "Handle branch A tasks"),
                    Map.of("name", "branch-b", "description", "Handle branch B tasks")
            );

            NodeExecutionContext context = buildContextWithRoutes("Handle branch A tasks", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getBranchesToFollow()).isNotNull();
            assertThat(result.getBranchesToFollow())
                    .contains(result.getOutput().get("selectedRoute").toString());
        }
    }

    // ==================== Default Route & Fallback ====================

    @Nested
    @DisplayName("Default Route and Fallback")
    class DefaultRouteAndFallback {

        @Test
        void usesDefaultRouteWhenNoMatch() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "billing", "description", "Handle billing invoices and payment"),
                    Map.of("name", "support", "description", "Handle technical support issues")
            );

            NodeExecutionContext context = buildContextWithRoutes(
                    "xyz123 completely unrelated content zzz999", routes, "general");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            String selectedRoute = (String) result.getOutput().get("selectedRoute");
            assertThat(selectedRoute).isNotEmpty();
        }

        @Test
        void fallsBackToFirstRouteWhenNoDefaultConfigured() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "firstRoute", "description", "Something alpha beta"),
                    Map.of("name", "secondRoute", "description", "Another thing gamma delta")
            );

            NodeExecutionContext context = buildContextWithRoutes(
                    "xyz123 unrelated input no keyword overlap whatsoever zzz999", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            String selectedRoute = (String) result.getOutput().get("selectedRoute");
            assertThat(selectedRoute).isNotEmpty();
        }
    }

    // ==================== Input Sources ====================

    @Nested
    @DisplayName("Input Sources")
    class InputSources {

        @Test
        void readsPromptFromInputDataField() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "greet", "description", "Handle greetings hello hi welcome")
            );

            Map<String, Object> config = new HashMap<>();
            config.put("routes", routes);

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("input", "Hello, welcome to the system!");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("router-1")
                    .nodeType("aiRouter")
                    .nodeConfig(config)
                    .inputData(inputData)
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("selectedRoute")).isEqualTo("greet");
        }

        @Test
        void readsPromptFromDataField() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "process", "description", "Process data records and files")
            );

            Map<String, Object> config = new HashMap<>();
            config.put("routes", routes);

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("data", "process these data records and files");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("router-1")
                    .nodeType("aiRouter")
                    .nodeConfig(config)
                    .inputData(inputData)
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("selectedRoute")).isEqualTo("process");
        }

        @Test
        void readsPromptFromPromptField() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "analyze", "description", "Analyze reports and statistics")
            );

            Map<String, Object> config = new HashMap<>();
            config.put("routes", routes);

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("prompt", "analyze these reports and statistics");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("router-1")
                    .nodeType("aiRouter")
                    .nodeConfig(config)
                    .inputData(inputData)
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("selectedRoute")).isEqualTo("analyze");
        }

        @Test
        void configPromptTakesPrecedenceOverInputData() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "billing", "description", "Handle billing invoices and payment"),
                    Map.of("name", "support", "description", "Handle technical support issues")
            );

            Map<String, Object> config = new HashMap<>();
            config.put("prompt", "billing invoice and payment help");
            config.put("routes", routes);

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("input", "technical support issues help");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("router-1")
                    .nodeType("aiRouter")
                    .nodeConfig(config)
                    .inputData(inputData)
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("selectedRoute")).isEqualTo("billing");
        }
    }

    // ==================== Validation & Error Cases ====================

    @Nested
    @DisplayName("Validation and Error Cases")
    class ValidationAndErrors {

        @Test
        void emptyPromptAndNoInputData_returnsFailure() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "route1", "description", "Some route")
            );

            NodeExecutionContext context = buildContextWithRoutes("", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Input prompt is required");
        }

        @Test
        void noPromptAndEmptyInputData_returnsFailure() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "route1", "description", "Some route")
            );

            Map<String, Object> config = new HashMap<>();
            config.put("routes", routes);

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("router-1")
                    .nodeType("aiRouter")
                    .nodeConfig(config)
                    .inputData(Map.of())
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Input prompt is required");
        }

        @Test
        void emptyRoutesList_returnsFailure() {
            NodeExecutionContext context = buildContextWithRoutes("route this message", List.of(), "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("At least one route must be defined");
        }

        @Test
        void noRoutesKeyInConfig_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("prompt", "route this message");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("router-1")
                    .nodeType("aiRouter")
                    .nodeConfig(config)
                    .inputData(Map.of())
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("At least one route must be defined");
        }

        @Test
        void routeWithoutNameIsSkipped() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("description", "Route without name"),
                    Map.of("name", "valid", "description", "Valid route description")
            );

            NodeExecutionContext context = buildContextWithRoutes("valid route test", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> allRoutes = (List<String>) result.getOutput().get("allRoutes");
            assertThat(allRoutes).containsExactly("valid");
        }

        @Test
        void routeWithEmptyDescription_lowPriorityOverRouteWithDescription() {
            List<Map<String, Object>> routes = List.of(
                    Map.of("name", "empty-desc"),
                    Map.of("name", "has-desc", "description", "This route handles keyword matching test")
            );

            NodeExecutionContext context = buildContextWithRoutes(
                    "keyword matching test", routes, "");

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("selectedRoute")).isEqualTo("has-desc");
        }
    }

    // ==================== Helper Methods ====================

    private NodeExecutionContext buildContextWithRoutes(String prompt,
                                                        List<Map<String, Object>> routes,
                                                        String defaultRoute) {
        Map<String, Object> config = new HashMap<>();
        config.put("prompt", prompt);
        config.put("routes", routes);
        if (!defaultRoute.isEmpty()) {
            config.put("defaultRoute", defaultRoute);
        }

        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("router-1")
                .nodeType("aiRouter")
                .nodeConfig(config)
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
