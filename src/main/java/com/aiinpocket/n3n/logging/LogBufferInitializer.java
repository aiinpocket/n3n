package com.aiinpocket.n3n.logging;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Spring component that bridges the Logback appender with the Spring-managed buffer.
 * On startup, it registers the InMemoryLogBuffer with the static LogBufferAppender.
 */
@Component
@RequiredArgsConstructor
public class LogBufferInitializer {

    private final InMemoryLogBuffer buffer;

    @PostConstruct
    public void init() {
        LogBufferAppender.setBuffer(buffer);
    }
}
