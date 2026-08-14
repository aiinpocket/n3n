package com.aiinpocket.n3n.execution.handler.handlers.scripting;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
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

    private final ExecutorService executor = Executors.newFixedThreadPool(
        Math.max(4, Runtime.getRuntime().availableProcessors()),
        r -> {
            Thread t = new Thread(r, "js-executor");
            t.setDaemon(true);
            return t;
        });

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down JavaScriptEngine executor...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

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
                .allowHostAccess(HostAccess.NONE)
                .allowNativeAccess(false)
                .allowIO(false)
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

                // n8n 風格相容層：AI 生成的程式碼常用 $input.item(n)/.first()/.last()/.all() 與 .json，
                // 這裡以不可列舉屬性補上，同時保留原本「$input 即資料物件」的用法
                String compatSetup = """
                    (function () {
                        const raw = $input;
                        const isNumericKeys = (keys) => keys.length > 0 && keys.every(k => /^\\d+$/.test(k));
                        const toItems = (d) => {
                            if (d === null || d === undefined) return [];
                            if (Array.isArray(d)) return d;
                            if (typeof d !== 'object') return [d];
                            const keys = Object.keys(d);
                            if (keys.length === 1) {
                                const v = d[keys[0]];
                                if (Array.isArray(v)) return v;
                                if (v && typeof v === 'object') {
                                    const vk = Object.keys(v);
                                    if (isNumericKeys(vk)) {
                                        return vk.sort((a, b) => Number(a) - Number(b)).map(k => v[k]);
                                    }
                                    if (keys[0] === 'merged') return Object.values(v);
                                }
                            }
                            if (isNumericKeys(keys)) {
                                return keys.sort((a, b) => Number(a) - Number(b)).map(k => d[k]);
                            }
                            return [d];
                        };
                        const wrap = (v) => {
                            if (v !== null && typeof v === 'object') {
                                if (!('json' in v)) {
                                    try { Object.defineProperty(v, 'json', { value: v, enumerable: false }); } catch (e) {}
                                }
                                return v;
                            }
                            return { json: v };
                        };
                        const items = toItems(raw).map(wrap);
                        const helpers = {
                            item: (i) => items[i],
                            first: () => items[0],
                            last: () => items[items.length - 1],
                            all: () => items,
                        };
                        if (raw !== null && typeof raw === 'object' && !Array.isArray(raw)) {
                            for (const name of Object.keys(helpers)) {
                                if (!(name in raw)) {
                                    try { Object.defineProperty(raw, name, { value: helpers[name], enumerable: false }); } catch (e) {}
                                }
                            }
                            wrap(raw);
                        }
                    })();
                    """;
                context.eval("js", compatSetup);

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

                // 帶回具體錯誤訊息（使用者自己的腳本錯誤），方便除錯與 AI 自動修復
                String detail = e.getMessage();
                String errorMessage = (detail == null || detail.isBlank())
                    ? "Script execution failed"
                    : "Script execution failed: " + detail;

                return ScriptResult.builder()
                    .success(false)
                    .errorType(errorType)
                    .errorMessage(errorMessage)
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
            log.error("Script execution error: {}", cause.getMessage(), cause);
            throw new ScriptExecutionException("EXECUTION_ERROR", "Script execution failed", cause);
        }
    }

    @Override
    public boolean validateSyntax(String code) {
        try (Context context = Context.newBuilder("js")
            .allowAllAccess(false)
            .allowHostAccess(HostAccess.NONE)
            .allowNativeAccess(false)
            .allowIO(false)
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
