package com.aiinpocket.n3n.logging.controller;

import com.aiinpocket.n3n.logging.InMemoryLogBuffer;
import com.aiinpocket.n3n.logging.LogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * REST controller for querying historical logs and streaming real-time log events via SSE.
 */
@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Tag(name = "Logs", description = "Log viewer and streaming")
@PreAuthorize("hasRole('ADMIN')")
public class LogViewerController {

    private static final int MAX_SSE_CONNECTIONS_PER_USER = 3;
    private final ConcurrentHashMap<UUID, AtomicInteger> activeConnections = new ConcurrentHashMap<>();

    private final InMemoryLogBuffer logBuffer;
    private final ObjectMapper objectMapper;

    /**
     * Query historical log entries with optional filters.
     *
     * @param level  filter by log level (INFO, WARN, ERROR, DEBUG)
     * @param search free-text search across message, logger, and context fields
     * @param limit  maximum number of entries to return (default 100)
     * @return list of matching log entries, most recent first
     */
    @GetMapping
    public List<LogEntry> getLogs(
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 20) String level,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 500) String search,
            @RequestParam(defaultValue = "100") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(1000) int limit) {
        return logBuffer.query(level, search, limit);
    }

    /**
     * Stream real-time log entries via Server-Sent Events.
     * Each new log entry is sent as a JSON-encoded SSE event.
     * The connection remains open until the client disconnects.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.core.userdetails.UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        // Limit concurrent SSE connections per user to prevent resource exhaustion
        AtomicInteger count = activeConnections.computeIfAbsent(userId, k -> new AtomicInteger(0));
        if (count.get() >= MAX_SSE_CONNECTIONS_PER_USER) {
            throw new IllegalStateException("Too many concurrent SSE connections (max " + MAX_SSE_CONNECTIONS_PER_USER + ")");
        }
        count.incrementAndGet();

        // 5-minute timeout to prevent abandoned connections from leaking resources
        SseEmitter emitter = new SseEmitter(300_000L);

        Consumer<LogEntry> listener = entry -> {
            try {
                String json = objectMapper.writeValueAsString(entry);
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(json, MediaType.APPLICATION_JSON));
            } catch (java.io.IOException e) {
                log.debug("SSE log stream client disconnected");
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.warn("Failed to send log entry via SSE: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        };

        logBuffer.addListener(listener);

        // Clean up listener and connection count on completion, timeout, or error
        Runnable cleanupWithListener = () -> {
            logBuffer.removeListener(listener);
            count.decrementAndGet();
            if (count.get() <= 0) {
                activeConnections.remove(userId);
            }
        };
        emitter.onCompletion(cleanupWithListener);
        emitter.onTimeout(cleanupWithListener);
        emitter.onError(ex -> cleanupWithListener.run());

        log.debug("New SSE log stream client connected (user={}, active={})", userId, count.get());
        return emitter;
    }
}
