package com.aiinpocket.n3n.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;

/**
 * Custom Logback appender that forwards log events to the in-memory log buffer.
 * The buffer is injected via a static setter after Spring context initialization.
 */
public class LogBufferAppender extends AppenderBase<ILoggingEvent> {

    private static volatile InMemoryLogBuffer buffer;

    public static void setBuffer(InMemoryLogBuffer buf) {
        buffer = buf;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (buffer == null) {
            return;
        }

        LogEntry entry = LogEntry.builder()
                .timestamp(Instant.ofEpochMilli(event.getTimeStamp()))
                .level(event.getLevel().toString())
                .logger(shortenLogger(event.getLoggerName()))
                .message(event.getFormattedMessage())
                .traceId(event.getMDCPropertyMap().getOrDefault("traceId", null))
                .executionId(event.getMDCPropertyMap().getOrDefault("executionId", null))
                .flowId(event.getMDCPropertyMap().getOrDefault("flowId", null))
                .nodeId(event.getMDCPropertyMap().getOrDefault("nodeId", null))
                .userId(event.getMDCPropertyMap().getOrDefault("userId", null))
                .threadName(event.getThreadName())
                .build();

        buffer.add(entry);
    }

    /**
     * Shorten the logger name to just the class name.
     * e.g. "com.aiinpocket.n3n.flow.service.FlowService" -> "FlowService"
     */
    private String shortenLogger(String name) {
        int lastDot = name.lastIndexOf('.');
        return lastDot >= 0 ? name.substring(lastDot + 1) : name;
    }
}
