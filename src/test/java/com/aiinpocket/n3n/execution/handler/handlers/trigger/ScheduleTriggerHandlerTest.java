package com.aiinpocket.n3n.execution.handler.handlers.trigger;

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
            // 沒指定時區時採用平台預設（n3n.default-timezone），不是容器的 UTC——
            // 否則使用者說的「早上 9 點」在台北會變成下午 5 點才跑
            assertThat(result.getOutput()).containsEntry("timezone", "Asia/Taipei");
            assertThat(result.getOutput()).containsEntry("scheduleType", "cron");
        }

        @Test
        void configSchema_timezoneDefault_isPlatformDefaultNotUtc() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties =
                (Map<String, Object>) handler.getConfigSchema().get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> timezone = (Map<String, Object>) properties.get("timezone");

            // 設定表單會把 schema 的 default 直接填進節點，寫死 UTC 等於幫使用者選錯時區
            assertThat(timezone).containsEntry("default", "Asia/Taipei");
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
        void execute_aiGeneratedConfig_cronAliasAndFiveFieldExpression_succeeds() {
            // AI 生成流程實際輸出的格式（Joseph 查看財報案例）
            Map<String, Object> config = new HashMap<>();
            config.put("cron", "0 9 * * *");
            config.put("timezone", "Asia/Taipei");
            config.put("label", "每日早上 9 點觸發");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("nextExecution");
            assertThat(result.getOutput()).containsEntry("timezone", "Asia/Taipei");
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
        void validateConfig_cronAlias_returnsValid() {
            Map<String, Object> config = new HashMap<>();
            config.put("cron", "0 9 * * *");

            ValidationResult result = handler.validateConfig(config);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        void validateConfig_fiveFieldUnixCron_returnsValid() {
            Map<String, Object> config = new HashMap<>();
            config.put("scheduleType", "cron");
            config.put("cronExpression", "0 9 * * *");

            ValidationResult result = handler.validateConfig(config);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        void normalizeCron_fiveFields_prependsSeconds() {
            assertThat(ScheduleTriggerHandler.normalizeCron("0 9 * * *")).isEqualTo("0 0 9 * * *");
            assertThat(ScheduleTriggerHandler.normalizeCron("0 0 9 * * *")).isEqualTo("0 0 9 * * *");
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
