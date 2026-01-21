package com.aiinpocket.n3n.execution.handler.handlers.scripting;

import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * JavaScript execution engine using GraalVM Polyglot.
 */
@Component
@Slf4j
public class JavaScriptEngine implements ScriptEngine {

    private static final long DEFAULT_TIMEOUT_MS = 30000; // 30 seconds
    private static final long MAX_TIMEOUT_MS = 300000; // 5 minutes

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "js-executor");
        t.setDaemon(true);
        return t;
    });

    @Override
    public String getLanguage() {
        return "javascript";
    }

    @Override
    public ScriptResult execute(String code, Map<String, Object> input, long timeout) throws ScriptExecutionException {
        if (timeout <= 0) {
            timeout = DEFAULT_TIMEOUT_MS;
        }
        if (timeout > MAX_TIMEOUT_MS) {
            timeout = MAX_TIMEOUT_MS;
        }

        long startTime = System.currentTimeMillis();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        List<String> logs = Collections.synchronizedList(new ArrayList<>());

        Future<ScriptResult> future = executor.submit(() -> {
            try (Context context = Context.newBuilder("js")
                .allowAllAccess(false)
                .option("js.ecmascript-version", "2022")
                .option("engine.WarnInterpreterOnly", "false")
                .out(outputStream)
                .err(outputStream)
                .build()) {

                // Bind input data as $input
                Value bindings = context.getBindings("js");
                bindings.putMember("$input", convertToGraalValue(context, input));
                bindings.putMember("$json", convertToGraalValue(context, input));

                // Add console.log functionality
                String consoleSetup = """
                    const _logs = [];
                    const console = {
                        log: (...args) => _logs.push(args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' ')),
                        info: (...args) => _logs.push('[INFO] ' + args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' ')),
                        warn: (...args) => _logs.push('[WARN] ' + args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' ')),
                        error: (...args) => _logs.push('[ERROR] ' + args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' '))
                    };
                    """;

                context.eval("js", consoleSetup);

                // Wrap user code to return result
                String wrappedCode = """
                    (function() {
                        %s
                    })();
                    """.formatted(code);

                Source source = Source.newBuilder("js", wrappedCode, "user-script.js").build();
                Value result = context.eval(source);

                // Get logs
                Value logsValue = context.eval("js", "_logs");
                if (logsValue.hasArrayElements()) {
                    for (long i = 0; i < logsValue.getArraySize(); i++) {
                        logs.add(logsValue.getArrayElement(i).asString());
                    }
                }

                // Convert result to Java
                Object javaResult = convertFromGraalValue(result);

                long executionTime = System.currentTimeMillis() - startTime;

                if (javaResult instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mapResult = (Map<String, Object>) javaResult;
                    return ScriptResult.builder()
                        .success(true)
                        .data(mapResult)
                        .logs(logs)
                        .executionTimeMs(executionTime)
                        .build();
                } else {
                    return ScriptResult.builder()
                        .success(true)
                        .output(javaResult)
                        .logs(logs)
                        .executionTimeMs(executionTime)
                        .build();
                }

            } catch (PolyglotException e) {
                long executionTime = System.currentTimeMillis() - startTime;
                log.warn("JavaScript execution error: {}", e.getMessage());

                String errorType = e.isSyntaxError() ? "SYNTAX_ERROR" :
                    e.isResourceExhausted() ? "RESOURCE_EXHAUSTED" :
                        e.isCancelled() ? "CANCELLED" : "RUNTIME_ERROR";

                return ScriptResult.builder()
                    .success(false)
                    .errorType(errorType)
                    .errorMessage(e.getMessage())
                    .logs(logs)
                    .executionTimeMs(executionTime)
                    .build();
            }
        });

        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            long executionTime = System.currentTimeMillis() - startTime;
            return ScriptResult.builder()
                .success(false)
                .errorType("TIMEOUT")
                .errorMessage("Script execution timed out after " + timeout + "ms")
                .logs(logs)
                .executionTimeMs(executionTime)
                .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScriptExecutionException("INTERRUPTED", "Script execution was interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new ScriptExecutionException("EXECUTION_ERROR", cause.getMessage(), cause);
        }
    }

    @Override
    public boolean validateSyntax(String code) {
        try (Context context = Context.newBuilder("js")
            .allowAllAccess(false)
            .build()) {
            // Try to parse without executing
            Source source = Source.newBuilder("js", "(function(){" + code + "})", "syntax-check.js").build();
            context.parse(source);
            return true;
        } catch (PolyglotException e) {
            return !e.isSyntaxError();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        try (Context context = Context.newBuilder("js").build()) {
            context.eval("js", "1+1");
            return true;
        } catch (Exception e) {
            log.warn("JavaScript engine not available: {}", e.getMessage());
            return false;
        }
    }

    private Value convertToGraalValue(Context context, Object value) {
        if (value == null) {
            return context.eval("js", "null");
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            Value jsObject = context.eval("js", "({})");
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                jsObject.putMember(entry.getKey(), convertToGraalValue(context, entry.getValue()));
            }
            return jsObject;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            Value jsArray = context.eval("js", "[]");
            for (int i = 0; i < list.size(); i++) {
                jsArray.setArrayElement(i, convertToGraalValue(context, list.get(i)));
            }
            return jsArray;
        }
        // Primitives are handled automatically
        return context.asValue(value);
    }

    private Object convertFromGraalValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                list.add(convertFromGraalValue(value.getArrayElement(i)));
            }
            return list;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, convertFromGraalValue(value.getMember(key)));
            }
            return map;
        }
        // Fallback
        return value.toString();
    }
}
