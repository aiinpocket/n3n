package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ScheduleTriggerHandlerTest {

    private ScheduleTriggerHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ScheduleTriggerHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsScheduleTrigger() {
            assertThat(handler.getType()).isEqualTo("scheduleTrigger");
        }

        @Test
        void getDisplayName_returnsScheduleTrigger() {
            assertThat(handler.getDisplayName()).isEqualTo("Schedule Trigger");
        }

        @Test
        void getCategory_returnsTriggers() {
            assertThat(handler.getCategory()).isEqualTo("Triggers");
        }

        @Test
        void isTrigger_returnsTrue() {
            assertThat(handler.isTrigger()).isTrue();
        }

        @Test
        void getConfigSchema_containsProperties() {
            var schema = handler.getConfigSchema();
            assertThat(schema).containsKey("properties");
        }

        @Test
        void getInterfaceDefinition_hasEmptyInputs() {
            var iface = handler.getInterfaceDefinition();
            assertThat(iface).containsKey("inputs");
            assertThat(iface).containsKey("outputs");
        }
    }

    @Nested
    @DisplayName("Execution")
    class Execution {
        @Test
        void execute_validCronConfig_returnsTimestampAndTimezone() {
            Map<String, Object> config = new HashMap<>();
            config.put("cronExpression", "0 0 * * * *");
            NodeExecutionContext context = buildContext(config, null);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("triggeredAt");
            assertThat(result.getOutput()).containsEntry("timezone", "UTC");
            assertThat(result.getOutput()).containsEntry("scheduleType", "cron");
        }

        @Test
        void execute_withCronExpression_includesNextExecution() {
            Map<String, Object> config = new HashMap<>();
            config.put("cronExpression", "0 0 * * * *");
            config.put("timezone", "UTC");

            NodeExecutionContext context = buildContext(config, null);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("nextExecution");
            assertThat(result.getOutput()).containsEntry("cronExpression", "0 0 * * * *");
        }

        @Test
        void execute_withPayload_includesPayload() {
            Map<String, Object> config = new HashMap<>();
            config.put("cronExpression", "0 0 * * * *");
            config.put("payload", Map.of("key", "value"));

            NodeExecutionContext context = buildContext(config, null);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("payload");
        }

        @Test
        void execute_withCustomTimezone_usesSpecifiedTimezone() {
            Map<String, Object> config = new HashMap<>();
            config.put("cronExpression", "0 0 * * * *");
            config.put("timezone", "Asia/Tokyo");

            NodeExecutionContext context = buildContext(config, null);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("timezone", "Asia/Tokyo");
        }

        @Test
        void execute_invalidConfig_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            // Missing cronExpression for cron schedule type
            NodeExecutionContext context = buildContext(config, null);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("Config Validation")
    class ConfigValidation {
        @Test
        void validateConfig_validCron_returnsValid() {
            Map<String, Object> config = new HashMap<>();
            config.put("scheduleType", "cron");
            config.put("cronExpression", "0 0 * * * *");

            ValidationResult result = handler.validateConfig(config);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        void validateConfig_invalidCron_returnsInvalid() {
            Map<String, Object> config = new HashMap<>();
            config.put("scheduleType", "cron");
            config.put("cronExpression", "invalid-cron");

            ValidationResult result = handler.validateConfig(config);

            assertThat(result.isValid()).isFalse();
        }

        @Test
        void validateConfig_emptyCron_returnsInvalid() {
            Map<String, Object> config = new HashMap<>();
            config.put("scheduleType", "cron");
            config.put("cronExpression", "");

            ValidationResult result = handler.validateConfig(config);

            assertThat(result.isValid()).isFalse();
        }

        @Test
        void validateConfig_missingCron_returnsInvalid() {
            Map<String, Object> config = new HashMap<>();
            config.put("scheduleType", "cron");

            ValidationResult result = handler.validateConfig(config);

            assertThat(result.isValid()).isFalse();
        }

        @Test
        void validateConfig_validInterval_returnsValid() {
            Map<String, Object> config = new HashMap<>();
            config.put("scheduleType", "interval");
            config.put("interval", 5);

            ValidationResult result = handler.validateConfig(config);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        void validateConfig_zeroInterval_returnsInvalid() {
            Map<String, Object> config = new HashMap<>();
            config.put("scheduleType", "interval");
            config.put("interval", 0);

            ValidationResult result = handler.validateConfig(config);

            assertThat(result.isValid()).isFalse();
        }

        @Test
        void validateConfig_missingInterval_returnsInvalid() {
            Map<String, Object> config = new HashMap<>();
            config.put("scheduleType", "interval");

            ValidationResult result = handler.validateConfig(config);

            assertThat(result.isValid()).isFalse();
        }

        @Test
        void validateConfig_invalidTimezone_returnsInvalid() {
            Map<String, Object> config = new HashMap<>();
            config.put("scheduleType", "cron");
            config.put("cronExpression", "0 0 * * * *");
            config.put("timezone", "Invalid/Zone");

            ValidationResult result = handler.validateConfig(config);

            assertThat(result.isValid()).isFalse();
        }

        @Test
        void validateConfig_validTimezone_returnsValid() {
            Map<String, Object> config = new HashMap<>();
            config.put("scheduleType", "cron");
            config.put("cronExpression", "0 0 * * * *");
            config.put("timezone", "America/New_York");

            ValidationResult result = handler.validateConfig(config);

            assertThat(result.isValid()).isTrue();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("schedule-1")
                .nodeType("scheduleTrigger")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
