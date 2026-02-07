package com.aiinpocket.n3n.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

class InMemoryLogBufferTest {

    private InMemoryLogBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new InMemoryLogBuffer();
    }

    private LogEntry createEntry(String level, String message) {
        return LogEntry.builder()
            .timestamp(Instant.now())
            .level(level)
            .logger("TestLogger")
            .message(message)
            .threadName("test-thread")
            .build();
    }

    private LogEntry createEntry(String level, String message, Instant timestamp) {
        return LogEntry.builder()
            .timestamp(timestamp)
            .level(level)
            .logger("TestLogger")
            .message(message)
            .threadName("test-thread")
            .build();
    }

    @Nested
    @DisplayName("Add Entries")
    class AddEntries {

        @Test
        void add_singleEntry_incrementsSize() {
            buffer.add(createEntry("INFO", "test message"));

            assertThat(buffer.getSize()).isEqualTo(1);
        }

        @Test
        void add_multipleEntries_incrementsSize() {
            buffer.add(createEntry("INFO", "msg1"));
            buffer.add(createEntry("WARN", "msg2"));
            buffer.add(createEntry("ERROR", "msg3"));

            assertThat(buffer.getSize()).isEqualTo(3);
        }

        @Test
        void add_notifiesListeners() {
            List<LogEntry> received = new ArrayList<>();
            buffer.addListener(received::add);

            buffer.add(createEntry("INFO", "test"));

            assertThat(received).hasSize(1);
            assertThat(received.get(0).getMessage()).isEqualTo("test");
        }

        @Test
        void add_multipleListeners_allNotified() {
            AtomicInteger count1 = new AtomicInteger(0);
            AtomicInteger count2 = new AtomicInteger(0);

            buffer.addListener(e -> count1.incrementAndGet());
            buffer.addListener(e -> count2.incrementAndGet());

            buffer.add(createEntry("INFO", "test"));

            assertThat(count1.get()).isEqualTo(1);
            assertThat(count2.get()).isEqualTo(1);
        }

        @Test
        void add_brokenListener_removedSilently() {
            AtomicInteger goodCount = new AtomicInteger(0);
            Consumer<LogEntry> brokenListener = e -> { throw new RuntimeException("broken"); };

            buffer.addListener(brokenListener);
            buffer.addListener(e -> goodCount.incrementAndGet());

            buffer.add(createEntry("INFO", "test"));

            assertThat(goodCount.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Query Entries")
    class QueryEntries {

        @Test
        void query_noFilter_returnsAll() {
            buffer.add(createEntry("INFO", "msg1"));
            buffer.add(createEntry("WARN", "msg2"));
            buffer.add(createEntry("ERROR", "msg3"));

            List<LogEntry> result = buffer.query(null, null, 100);

            assertThat(result).hasSize(3);
        }

        @Test
        void query_filterByLevel_returnsMatchingOnly() {
            buffer.add(createEntry("INFO", "info msg"));
            buffer.add(createEntry("ERROR", "error msg"));
            buffer.add(createEntry("INFO", "another info"));

            List<LogEntry> result = buffer.query("ERROR", null, 100);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getLevel()).isEqualTo("ERROR");
        }

        @Test
        void query_filterByLevel_caseInsensitive() {
            buffer.add(createEntry("ERROR", "error msg"));

            List<LogEntry> result = buffer.query("error", null, 100);

            assertThat(result).hasSize(1);
        }

        @Test
        void query_filterBySearch_matchesMessage() {
            buffer.add(createEntry("INFO", "user login successful"));
            buffer.add(createEntry("INFO", "data processed"));

            List<LogEntry> result = buffer.query(null, "login", 100);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMessage()).contains("login");
        }

        @Test
        void query_filterBySearch_caseInsensitive() {
            buffer.add(createEntry("INFO", "User Login Successful"));

            List<LogEntry> result = buffer.query(null, "user login", 100);

            assertThat(result).hasSize(1);
        }

        @Test
        void query_filterBySearch_matchesLogger() {
            LogEntry entry = LogEntry.builder()
                .timestamp(Instant.now())
                .level("INFO")
                .logger("FlowService")
                .message("some message")
                .build();
            buffer.add(entry);

            List<LogEntry> result = buffer.query(null, "FlowService", 100);

            assertThat(result).hasSize(1);
        }

        @Test
        void query_filterBySearch_matchesContextFields() {
            LogEntry entry = LogEntry.builder()
                .timestamp(Instant.now())
                .level("INFO")
                .logger("Test")
                .message("msg")
                .executionId("exec-123")
                .flowId("flow-456")
                .build();
            buffer.add(entry);

            List<LogEntry> resultByExec = buffer.query(null, "exec-123", 100);
            List<LogEntry> resultByFlow = buffer.query(null, "flow-456", 100);

            assertThat(resultByExec).hasSize(1);
            assertThat(resultByFlow).hasSize(1);
        }

        @Test
        void query_combinedFilter_appliesBoth() {
            buffer.add(createEntry("INFO", "info login"));
            buffer.add(createEntry("ERROR", "error login"));
            buffer.add(createEntry("ERROR", "error other"));

            List<LogEntry> result = buffer.query("ERROR", "login", 100);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMessage()).isEqualTo("error login");
        }

        @Test
        void query_withLimit_respectsLimit() {
            for (int i = 0; i < 10; i++) {
                buffer.add(createEntry("INFO", "msg " + i));
            }

            List<LogEntry> result = buffer.query(null, null, 3);

            assertThat(result).hasSize(3);
        }

        @Test
        void query_returnsMostRecentFirst() {
            Instant now = Instant.now();
            buffer.add(createEntry("INFO", "oldest", now.minusSeconds(30)));
            buffer.add(createEntry("INFO", "middle", now.minusSeconds(15)));
            buffer.add(createEntry("INFO", "newest", now));

            List<LogEntry> result = buffer.query(null, null, 100);

            assertThat(result.get(0).getMessage()).isEqualTo("newest");
            assertThat(result.get(2).getMessage()).isEqualTo("oldest");
        }

        @Test
        void query_emptyBuffer_returnsEmpty() {
            List<LogEntry> result = buffer.query(null, null, 100);

            assertThat(result).isEmpty();
        }

        @Test
        void query_emptyLevelFilter_treatedAsNoFilter() {
            buffer.add(createEntry("INFO", "msg"));

            List<LogEntry> result = buffer.query("", null, 100);

            assertThat(result).hasSize(1);
        }

        @Test
        void query_emptySearchFilter_treatedAsNoFilter() {
            buffer.add(createEntry("INFO", "msg"));

            List<LogEntry> result = buffer.query(null, "", 100);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Since Timestamp")
    class SinceTimestamp {

        @Test
        void since_returnsEntriesAfterTimestamp() {
            Instant cutoff = Instant.now().minusSeconds(10);
            buffer.add(createEntry("INFO", "before", cutoff.minusSeconds(5)));
            buffer.add(createEntry("INFO", "after1", cutoff.plusSeconds(1)));
            buffer.add(createEntry("INFO", "after2", cutoff.plusSeconds(5)));

            List<LogEntry> result = buffer.since(cutoff);

            assertThat(result).hasSize(2);
        }

        @Test
        void since_returnsOldestFirst() {
            Instant cutoff = Instant.now().minusSeconds(100);
            buffer.add(createEntry("INFO", "first", cutoff.plusSeconds(1)));
            buffer.add(createEntry("INFO", "second", cutoff.plusSeconds(50)));

            List<LogEntry> result = buffer.since(cutoff);

            assertThat(result.get(0).getMessage()).isEqualTo("first");
            assertThat(result.get(1).getMessage()).isEqualTo("second");
        }

        @Test
        void since_noMatchingEntries_returnsEmpty() {
            buffer.add(createEntry("INFO", "old", Instant.now().minusSeconds(100)));

            List<LogEntry> result = buffer.since(Instant.now());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Listener Management")
    class ListenerManagement {

        @Test
        void removeListener_stopsNotifications() {
            AtomicInteger count = new AtomicInteger(0);
            Consumer<LogEntry> listener = e -> count.incrementAndGet();

            buffer.addListener(listener);
            buffer.add(createEntry("INFO", "first"));
            assertThat(count.get()).isEqualTo(1);

            buffer.removeListener(listener);
            buffer.add(createEntry("INFO", "second"));
            assertThat(count.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @Test
        void concurrentAdds_maintainsConsistency() throws InterruptedException {
            int numThreads = 10;
            int entriesPerThread = 100;
            CountDownLatch latch = new CountDownLatch(numThreads);
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            for (int t = 0; t < numThreads; t++) {
                int threadNum = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < entriesPerThread; i++) {
                            buffer.add(createEntry("INFO", "thread-" + threadNum + "-msg-" + i));
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(buffer.getSize()).isEqualTo(numThreads * entriesPerThread);
        }
    }

    @Nested
    @DisplayName("Buffer Size")
    class BufferSize {

        @Test
        void getSize_emptyBuffer_returnsZero() {
            assertThat(buffer.getSize()).isEqualTo(0);
        }
    }
}
