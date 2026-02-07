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
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "100") int limit) {
        return logBuffer.query(level, search, limit);
    }

    /**
     * Stream real-time log entries via Server-Sent Events.
     * Each new log entry is sent as a JSON-encoded SSE event.
     * The connection remains open until the client disconnects.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs() {
        // No timeout - keep connection open indefinitely
        SseEmitter emitter = new SseEmitter(0L);

        Consumer<LogEntry> listener = entry -> {
            try {
                String json = objectMapper.writeValueAsString(entry);
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(json, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        };

        logBuffer.addListener(listener);

        // Clean up listener on completion, timeout, or error
        emitter.onCompletion(() -> logBuffer.removeListener(listener));
        emitter.onTimeout(() -> logBuffer.removeListener(listener));
        emitter.onError(ex -> logBuffer.removeListener(listener));

        log.debug("New SSE log stream client connected");
        return emitter;
    }
}
