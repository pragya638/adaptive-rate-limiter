package com.example.ratelimiter.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.example.ratelimiter.history.DashboardHistoryDto.AnomalyRecord;
import com.example.ratelimiter.history.DashboardHistoryDto.DecisionRecord;
import com.example.ratelimiter.history.DashboardHistoryDto.LimitRecord;
import com.example.ratelimiter.history.DashboardHistoryDto.TrafficRecord;

class DashboardHistoryStoreTest {

    @Test
    void keepsOnlyTheNewestRecordsForEachSeries() {
        DashboardHistoryStore store = new DashboardHistoryStore(2);

        for (int i = 1; i <= 4; i++) {
            store.recordTraffic(traffic(i));
            store.recordEvaluation(anomaly(i), decision(i), limit(i));
        }

        DashboardHistoryStore.Snapshot snapshot = store.snapshot();

        assertThat(snapshot.traffic())
                .extracting(TrafficRecord::windowSequence)
                .containsExactly(3L, 4L);
        assertThat(snapshot.anomalies())
                .extracting(AnomalyRecord::windowSequence)
                .containsExactly(3L, 4L);
        assertThat(snapshot.decisions())
                .extracting(DecisionRecord::windowSequence)
                .containsExactly(3L, 4L);
        assertThat(snapshot.limits())
                .extracting(LimitRecord::windowSequence)
                .containsExactly(3L, 4L);
    }

    @Test
    void ignoresDuplicateAndOlderSequences() {
        DashboardHistoryStore store = new DashboardHistoryStore(10);

        store.recordTraffic(traffic(2));
        store.recordTraffic(traffic(2));
        store.recordTraffic(traffic(1));

        assertThat(store.snapshot().traffic())
                .extracting(TrafficRecord::windowSequence)
                .containsExactly(2L);
    }

    @Test
    void snapshotsAreImmutableCopies() {
        DashboardHistoryStore store = new DashboardHistoryStore(10);
        store.recordTraffic(traffic(1));

        List<TrafficRecord> snapshot = store.snapshot().traffic();
        store.recordTraffic(traffic(2));

        assertThat(snapshot).hasSize(1);
        assertThatThrownBy(() -> snapshot.add(traffic(3)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void concurrentReadersAndWriterDoNotCorruptTheStore() throws Exception {
        DashboardHistoryStore store = new DashboardHistoryStore(25);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger snapshotCount = new AtomicInteger();
        CountDownLatch readersReady = new CountDownLatch(3);

        List<Thread> readers = List.of(reader(store, running, snapshotCount, readersReady),
                reader(store, running, snapshotCount, readersReady),
                reader(store, running, snapshotCount, readersReady));
        readers.forEach(Thread::start);
        assertThat(readersReady.await(5, TimeUnit.SECONDS)).isTrue();

        Thread writer = new Thread(() -> {
            for (int i = 1; i <= 200; i++) {
                store.recordTraffic(traffic(i));
                store.recordEvaluation(anomaly(i), decision(i), limit(i));
            }
        });
        writer.start();
        writer.join(5000);
        running.set(false);
        for (Thread reader : readers) {
            reader.join(5000);
        }

        assertThat(writer.isAlive()).isFalse();
        assertThat(readers).allMatch(thread -> !thread.isAlive());
        assertThat(snapshotCount.get()).isPositive();
        assertThat(store.snapshot().traffic()).hasSize(25);
        assertThat(store.snapshot().decisions()).hasSize(25);
    }

    private Thread reader(DashboardHistoryStore store, AtomicBoolean running, AtomicInteger count,
            CountDownLatch ready) {
        return new Thread(() -> {
            ready.countDown();
            while (running.get()) {
                assertThatCode(store::snapshot).doesNotThrowAnyException();
                count.incrementAndGet();
            }
        });
    }

    private TrafficRecord traffic(long sequence) {
        return new TrafficRecord(sequence, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:30Z"), 10, 9, 1, 0.0, 0.33, 0.3, 0.01);
    }

    private AnomalyRecord anomaly(long sequence) {
        return new AnomalyRecord(sequence, Instant.parse("2026-01-01T00:00:31Z"),
                true, false, -0.1, "normal", "IsolationForest");
    }

    private DecisionRecord decision(long sequence) {
        return new DecisionRecord(sequence, Instant.parse("2026-01-01T00:00:31Z"),
                "KEEP_LIMIT", 5, 5, 5, "NORMAL_TRAFFIC", "Traffic is within the target range.");
    }

    private LimitRecord limit(long sequence) {
        return new LimitRecord(sequence, Instant.parse("2026-01-01T00:00:31Z"),
                5, 5, "KEEP_LIMIT");
    }
}
