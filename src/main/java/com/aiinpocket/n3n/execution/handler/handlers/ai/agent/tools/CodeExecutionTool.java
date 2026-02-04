package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 程式碼執行工具
 * 讓 AI Agent 能夠執行 JavaScript 程式碼
 *
 * 使用 GraalVM Polyglot 在沙盒環境中執行
 */
@Component
@Slf4j
public class CodeExecutionTool implements AgentNodeTool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LENGTH = 10000;

    @Override
    public String getId() {
        return "code_execution";
    }

    @Override
    public String getName() {
        return "Code Execution";
    }

    @Override
    public String getDescription() {
        return "Execute JavaScript code in a sandboxed environment. " +
               "Use this for data transformation, calculations, string manipulation, or any programmatic task. " +
               "The code runs in isolation with no access to file system or network.";
    }

    @Override
    public String getCategory() {
        return "utility";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;  // 程式碼執行需要確認
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "code", Map.of(
                    "type", "string",
                    "description", "JavaScript code to execute. The last expression value will be returned."
                ),
                "variables", Map.of(
                    "type", "object",
                    "description", "Variables to inject into the execution context",
                    "additionalProperties", true
                ),
                "timeout", Map.of(
                    "type", "integer",
                    "description", "Execution timeout in seconds",
                    "default", DEFAULT_TIMEOUT_SECONDS
                )
            ),
            "required", List.of("code")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            String code = (String) parameters.get("code");
            Map<String, Object> variables = (Map<String, Object>) parameters.getOrDefault("variables", Map.of());
            int timeout = ((Number) parameters.getOrDefault("timeout", DEFAULT_TIMEOUT_SECONDS)).intValue();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

            try (Context jsContext = Context.newBuilder("js")
                    .allowAllAccess(false)
                    .option("engine.WarnInterpreterOnly", "false")
                    .out(outputStream)
                    .err(errorStream)
                    .build()) {

                // 注入變數
                Value bindings = jsContext.getBindings("js");
                for (Map.Entry<String, Object> entry : variables.entrySet()) {
                    bindings.putMember(entry.getKey(), entry.getValue());
                }

                // 注入 console.log
                jsContext.eval("js", """
                    var console = {
                        log: function() {
                            var args = Array.prototype.slice.call(arguments);
                            print(args.map(function(a) {
                                return typeof a === 'object' ? JSON.stringify(a) : String(a);
                            }).join(' '));
                        },
                        error: function() {
                            console.log.apply(console, arguments);
                        }
                    };
                    """);

                // 執行程式碼（帶超時）
                CompletableFuture<Value> future = CompletableFuture.supplyAsync(() ->
                    jsContext.eval("js", code)
                );

                Value result;
                try {
                    result = future.get(timeout, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    return ToolResult.failure("Code execution timed out after " + timeout + " seconds");
                }

                // 收集輸出
                String stdout = outputStream.toString();
                String stderr = errorStream.toString();

                // 格式化結果
                String resultStr = formatResult(result);
                StringBuilder output = new StringBuilder();

                if (!stdout.isEmpty()) {
                    output.append("Console Output:\n").append(stdout).append("\n");
                }
                if (!stderr.isEmpty()) {
                    output.append("Errors:\n").append(stderr).append("\n");
                }
                output.append("Return Value: ").append(resultStr);

                String finalOutput = truncateIfNeeded(output.toString(), MAX_OUTPUT_LENGTH);

                log.debug("Code execution completed, output length: {}", finalOutput.length());

                return ToolResult.success(finalOutput, Map.of(
                    "result", resultStr,
                    "stdout", stdout,
                    "stderr", stderr
                ));

            } catch (PolyglotException e) {
                String errorMessage = e.getMessage();
                if (e.isGuestException()) {
                    errorMessage = "JavaScript error: " + e.getMessage();
                }
                log.error("Code execution failed: {}", errorMessage);
                return ToolResult.failure(errorMessage);
            } catch (Exception e) {
                log.error("Code execution failed: {}", e.getMessage());
                return ToolResult.failure("Code execution failed: " + e.getMessage());
            }
        });
    }

    private String formatResult(Value result) {
        if (result == null || result.isNull()) {
            return "null";
        }
        if (result.isString()) {
            return result.asString();
        }
        if (result.isNumber()) {
            return String.valueOf(result.as(Number.class));
        }
        if (result.isBoolean()) {
            return String.valueOf(result.asBoolean());
        }
        if (result.hasArrayElements()) {
            StringBuilder sb = new StringBuilder("[");
            long size = Math.min(result.getArraySize(), 100);  // 限制顯示數量
            for (long i = 0; i < size; i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatResult(result.getArrayElement(i)));
            }
            if (result.getArraySize() > 100) {
                sb.append(", ... (").append(result.getArraySize() - 100).append(" more)");
            }
            sb.append("]");
            return sb.toString();
        }
        if (result.hasMembers()) {
            return result.toString();
        }
        return result.toString();
    }

    private String truncateIfNeeded(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "... (truncated)";
    }
}
