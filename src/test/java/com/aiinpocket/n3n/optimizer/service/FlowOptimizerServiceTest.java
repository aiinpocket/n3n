package com.aiinpocket.n3n.optimizer.service;

import com.aiinpocket.n3n.optimizer.config.FlowOptimizerConfig;
import com.aiinpocket.n3n.optimizer.dto.FlowOptimizationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FlowOptimizerServiceTest {

    private FlowOptimizerConfig config;
    private ObjectMapper objectMapper;
    private FlowOptimizerService service;

    @BeforeEach
    void setUp() {
        config = new FlowOptimizerConfig();
        objectMapper = new ObjectMapper();
        service = new FlowOptimizerService(config, objectMapper);
    }

    @Nested
    @DisplayName("Analyze Flow - Disabled")
    class AnalyzeFlowDisabled {

        @Test
        void analyzeFlow_whenDisabled_returnsDisabledResponse() {
            config.setEnabled(false);

            FlowOptimizationResponse result = service.analyzeFlow(Map.of("nodes", List.of()));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getSuggestions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Config Defaults")
    class ConfigDefaults {

        @Test
        void config_hasCorrectDefaults() {
            FlowOptimizerConfig defaultConfig = new FlowOptimizerConfig();

            assertThat(defaultConfig.isEnabled()).isTrue();
            assertThat(defaultConfig.getUrl()).isEqualTo("http://localhost:8081");
            assertThat(defaultConfig.getTimeoutMs()).isEqualTo(30000);
            assertThat(defaultConfig.getModel()).isEqualTo("phi-3-mini");
            assertThat(defaultConfig.getTemperature()).isEqualTo(0.7);
            assertThat(defaultConfig.getMaxTokens()).isEqualTo(1024);
        }
    }

    @Nested
    @DisplayName("Parse Response")
    class ParseResponse {

        @Test
        void parseResponse_validJson_returnsSuggestions() throws Exception {
            String content = """
                {
                  "suggestions": [
                    {
                      "type": "parallel",
                      "title": "Run nodes in parallel",
                      "description": "Nodes A and B have no dependencies",
                      "affectedNodes": ["nodeA", "nodeB"],
                      "priority": 1
                    }
                  ]
                }
                """;

            Method parseMethod = FlowOptimizerService.class.getDeclaredMethod("parseResponse", String.class);
            parseMethod.setAccessible(true);
            FlowOptimizationResponse result = (FlowOptimizationResponse) parseMethod.invoke(service, content);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getSuggestions()).hasSize(1);
            assertThat(result.getSuggestions().get(0).getType()).isEqualTo("parallel");
            assertThat(result.getSuggestions().get(0).getTitle()).isEqualTo("Run nodes in parallel");
            assertThat(result.getSuggestions().get(0).getAffectedNodes()).containsExactly("nodeA", "nodeB");
            assertThat(result.getSuggestions().get(0).getPriority()).isEqualTo(1);
        }

        @Test
        void parseResponse_markdownCodeBlock_extractsJson() throws Exception {
            String content = """
                Here are some suggestions:
                ```json
                {
                  "suggestions": [
                    {
                      "type": "merge",
                      "title": "Merge HTTP calls",
                      "description": "Combine calls to same API",
                      "affectedNodes": ["http1", "http2"],
                      "priority": 2
                    }
                  ]
                }
                ```
                """;

            Method parseMethod = FlowOptimizerService.class.getDeclaredMethod("parseResponse", String.class);
            parseMethod.setAccessible(true);
            FlowOptimizationResponse result = (FlowOptimizationResponse) parseMethod.invoke(service, content);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getSuggestions()).hasSize(1);
            assertThat(result.getSuggestions().get(0).getType()).isEqualTo("merge");
        }

        @Test
        void parseResponse_emptySuggestions_returnsEmptyList() throws Exception {
            String content = """
                {"suggestions": []}
                """;

            Method parseMethod = FlowOptimizerService.class.getDeclaredMethod("parseResponse", String.class);
            parseMethod.setAccessible(true);
            FlowOptimizationResponse result = (FlowOptimizationResponse) parseMethod.invoke(service, content);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getSuggestions()).isEmpty();
        }

        @Test
        void parseResponse_invalidJson_returnsError() throws Exception {
            String content = "This is not valid JSON at all";

            Method parseMethod = FlowOptimizerService.class.getDeclaredMethod("parseResponse", String.class);
            parseMethod.setAccessible(true);
            FlowOptimizationResponse result = (FlowOptimizationResponse) parseMethod.invoke(service, content);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).contains("Failed to parse");
        }

        @Test
        void parseResponse_missingFields_usesDefaults() throws Exception {
            String content = """
                {
                  "suggestions": [
                    {
                      "type": "reorder"
                    }
                  ]
                }
                """;

            Method parseMethod = FlowOptimizerService.class.getDeclaredMethod("parseResponse", String.class);
            parseMethod.setAccessible(true);
            FlowOptimizationResponse result = (FlowOptimizationResponse) parseMethod.invoke(service, content);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getSuggestions()).hasSize(1);
            assertThat(result.getSuggestions().get(0).getType()).isEqualTo("reorder");
            assertThat(result.getSuggestions().get(0).getTitle()).isEmpty();
            assertThat(result.getSuggestions().get(0).getPriority()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Extract JSON")
    class ExtractJson {

        @Test
        void extractJson_withCodeBlock_extractsContent() throws Exception {
            String content = "Some text\n```json\n{\"key\":\"value\"}\n```\nMore text";

            Method method = FlowOptimizerService.class.getDeclaredMethod("extractJson", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(service, content);

            assertThat(result).isEqualTo("{\"key\":\"value\"}");
        }

        @Test
        void extractJson_withoutCodeBlock_returnsOriginal() throws Exception {
            String content = "{\"key\":\"value\"}";

            Method method = FlowOptimizerService.class.getDeclaredMethod("extractJson", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(service, content);

            assertThat(result).isEqualTo("{\"key\":\"value\"}");
        }

        @Test
        void extractJson_withGenericCodeBlock_extractsContent() throws Exception {
            String content = "```\n{\"key\":\"value\"}\n```";

            Method method = FlowOptimizerService.class.getDeclaredMethod("extractJson", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(service, content);

            assertThat(result).isEqualTo("{\"key\":\"value\"}");
        }
    }

    @Nested
    @DisplayName("Service Available")
    class ServiceAvailable {

        @Test
        void isServiceAvailable_whenDisabled_returnsFalse() {
            config.setEnabled(false);

            boolean result = service.isServiceAvailable();

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("FlowOptimizationResponse static methods")
    class ResponseStaticMethods {

        @Test
        void error_createsErrorResponse() {
            FlowOptimizationResponse response = FlowOptimizationResponse.error("Something failed");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Something failed");
        }

        @Test
        void disabled_createsSuccessWithEmptySuggestions() {
            FlowOptimizationResponse response = FlowOptimizationResponse.disabled();

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getSuggestions()).isEmpty();
        }
    }
}
