package com.aiinpocket.n3n.logging;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Thread-safe in-memory log buffer with listener support for SSE streaming.
 * Stores the most recent log entries up to a configurable maximum size.
 */
@Component
public class InMemoryLogBuffer {

    private static final int MAX_SIZE = 2000;

    private final ConcurrentLinkedDeque<LogEntry> entries = new ConcurrentLinkedDeque<>();
    private final AtomicInteger size = new AtomicInteger(0);
    private final CopyOnWriteArrayList<Consumer<LogEntry>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Add a log entry to the buffer. If the buffer exceeds MAX_SIZE,
     * the oldest entries are removed. All registered listeners are notified.
     */
    public void add(LogEntry entry) {
        entries.addLast(entry);
        int currentSize = size.incrementAndGet();

        // Trim oldest entries if over max size
        while (currentSize > MAX_SIZE) {
            LogEntry removed = entries.pollFirst();
            if (removed != null) {
                currentSize = size.decrementAndGet();
            } else {
                break;
            }
        }

        // Notify all listeners
        for (Consumer<LogEntry> listener : listeners) {
            try {
                listener.accept(entry);
            } catch (Exception e) {
                // Remove broken listeners silently
                listeners.remove(listener);
            }
        }
    }

    /**
     * Query log entries with optional level and search filters.
     *
     * @param level  filter by log level (e.g. "ERROR", "WARN"), or null for all
     * @param search filter by substring match in message, logger, or context fields, or null for all
     * @param limit  maximum number of entries to return
     * @return filtered list of log entries, most recent first
     */
    public List<LogEntry> query(String level, String search, int limit) {
        List<LogEntry> result = new ArrayList<>();
        // Iterate in reverse order (newest first)
        var iterator = entries.descendingIterator();
        while (iterator.hasNext() && result.size() < limit) {
            LogEntry entry = iterator.next();
            if (matchesFilter(entry, level, search)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Return all entries after the given timestamp.
     *
     * @param timestamp the cutoff timestamp (exclusive)
     * @return list of entries after the timestamp, oldest first
     */
    public List<LogEntry> since(Instant timestamp) {
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry entry : entries) {
            if (entry.getTimestamp().isAfter(timestamp)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Register a listener that will be notified for each new log entry.
     */
    public void addListener(Consumer<LogEntry> listener) {
        listeners.add(listener);
    }

    /**
     * Remove a previously registered listener.
     */
    public void removeListener(Consumer<LogEntry> listener) {
        listeners.remove(listener);
    }

    /**
     * Get the current number of entries in the buffer.
     */
    public int getSize() {
        return size.get();
    }

    private boolean matchesFilter(LogEntry entry, String level, String search) {
        if (level != null && !level.isEmpty() && !level.equalsIgnoreCase(entry.getLevel())) {
            return false;
        }
        if (search != null && !search.isEmpty()) {
            String lowerSearch = search.toLowerCase();
            return containsIgnoreCase(entry.getMessage(), lowerSearch)
                    || containsIgnoreCase(entry.getLogger(), lowerSearch)
                    || containsIgnoreCase(entry.getTraceId(), lowerSearch)
                    || containsIgnoreCase(entry.getExecutionId(), lowerSearch)
                    || containsIgnoreCase(entry.getFlowId(), lowerSearch)
                    || containsIgnoreCase(entry.getNodeId(), lowerSearch)
                    || containsIgnoreCase(entry.getUserId(), lowerSearch)
                    || containsIgnoreCase(entry.getThreadName(), lowerSearch);
        }
        return true;
    }

    private boolean containsIgnoreCase(String value, String lowerSearch) {
        return value != null && value.toLowerCase().contains(lowerSearch);
    }
}
