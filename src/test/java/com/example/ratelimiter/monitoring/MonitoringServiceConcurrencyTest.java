package com.example.ratelimiter.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class MonitoringServiceConcurrencyTest {

    @Test
    void readersCannotObserveNewWindowWithPreviousEwma() throws Exception {
        MonitoringServiceTest.FixedClock clock = new MonitoringServiceTest.FixedClock(
                Instant.parse("2026-01-01T00:00:00Z"));
        BlockingTrafficMetrics metrics = new BlockingTrafficMetrics(clock);
        MonitoringService service = new MonitoringService(metrics,
                new MonitoringServiceTest.TestProps(0.5, Duration.ofSeconds(10)));

        metrics.recordRequest();
        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> updater = executor.submit(service::updateObservations);
            assertTrue(metrics.windowClosed.await(5, TimeUnit.SECONDS), "updater did not close the window");

            // The window and its sequence have already advanced, but the EWMA
            // update is still blocked inside the writer's critical section.
            // Readers must not be able to snapshot this mixed state.
            assertEquals(1L, metrics.getWindowSequence());
            assertEquals(0.0, service.getEwmaCalculator().getSmoothed(), 1e-9);

            CountDownLatch readerStarted = new CountDownLatch(1);
            CountDownLatch readerFinished = new CountDownLatch(1);
            Future<TrafficMetricsDto> reader = executor.submit(() -> {
                readerStarted.countDown();
                try {
                    return service.getMetrics();
                } finally {
                    readerFinished.countDown();
                }
            });

            assertTrue(readerStarted.await(5, TimeUnit.SECONDS), "reader did not start");
            assertFalse(readerFinished.await(300, TimeUnit.MILLISECONDS),
                    "reader returned a new window sequence with the previous EWMA");

            metrics.continueUpdate.countDown();
            updater.get(5, TimeUnit.SECONDS);
            TrafficMetricsDto snapshot = reader.get(5, TimeUnit.SECONDS);

            assertEquals(1L, snapshot.getWindowSequence());
            assertEquals(2L, snapshot.getWindowRequestCount());
            assertEquals(0.2, snapshot.getCurrentRequestRate(), 1e-9);
            assertEquals(0.2, snapshot.getEwmaRequestRate(), 1e-9);
        } finally {
            metrics.continueUpdate.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSnapshotsAlwaysPairSequenceWithItsOwnRateAndEwma() throws Exception {
        int windows = 60;
        Duration interval = Duration.ofSeconds(10);
        MonitoringServiceTest.FixedClock clock = new MonitoringServiceTest.FixedClock(
                Instant.parse("2026-02-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringService service = new MonitoringService(metrics,
                new MonitoringServiceTest.TestProps(0.5, interval));

        double[] expectedEwma = new double[windows + 1];
        double ewma = 0.0;
        for (int i = 1; i <= windows; i++) {
            double rate = requestCountForWindow(i) / (double) interval.toSeconds();
            ewma = (i == 1) ? rate : 0.5 * rate + 0.5 * ewma;
            expectedEwma[i] = ewma;
        }

        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> readerFutures = new ArrayList<>();
        AtomicBoolean stop = new AtomicBoolean(false);
        CountDownLatch start = new CountDownLatch(1);
        int readerCount = 4;
        ExecutorService pool = Executors.newFixedThreadPool(readerCount + 1);
        try {
            for (int r = 0; r < readerCount; r++) {
                readerFutures.add(pool.submit(() -> {
                    start.await();
                    while (!stop.get()) {
                        TrafficMetricsDto snapshot = service.getMetrics();
                        long sequence = snapshot.getWindowSequence();
                        if (sequence >= 1 && sequence <= windows) {
                            int index = (int) sequence;
                            double expectedRate = requestCountForWindow(index) / (double) interval.toSeconds();
                            if (Math.abs(snapshot.getCurrentRequestRate() - expectedRate) > 1e-9) {
                                failures.add("sequence " + sequence + ": rate=" + snapshot.getCurrentRequestRate()
                                        + " expected=" + expectedRate);
                            }
                            if (Math.abs(snapshot.getEwmaRequestRate() - expectedEwma[index]) > 1e-9) {
                                failures.add("sequence " + sequence + ": ewma=" + snapshot.getEwmaRequestRate()
                                        + " expected=" + expectedEwma[index]);
                            }
                        }
                    }
                    return null;
                }));
            }

            start.countDown();
            for (int i = 1; i <= windows; i++) {
                int requests = requestCountForWindow(i);
                for (int j = 0; j < requests; j++) {
                    metrics.recordRequest();
                }
                clock.advance(interval);
                service.updateObservations();
            }
        } finally {
            stop.set(true);
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "reader pool did not terminate");
        }

        for (Future<?> readerFuture : readerFutures) {
            readerFuture.get(5, TimeUnit.SECONDS);
        }
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    @Test
    void listenersRunOutsideTheWindowLockSoMetricsReadersAreNotBlocked() throws Exception {
        MonitoringServiceTest.FixedClock clock = new MonitoringServiceTest.FixedClock(
                Instant.parse("2026-03-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringProperties props = new MonitoringServiceTest.TestProps(0.5, Duration.ofSeconds(10));
        CompletedWindowStore store = new CompletedWindowStore();
        CountDownLatch listenerEntered = new CountDownLatch(1);
        CountDownLatch releaseListener = new CountDownLatch(1);
        CompletedWindowListener blockingListener = window -> {
            listenerEntered.countDown();
            try {
                if (!releaseListener.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to release listener");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while blocking the listener", e);
            }
        };
        MonitoringService service = new MonitoringService(metrics, props, store, List.of(blockingListener));

        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> updater = executor.submit(service::updateObservations);
            assertTrue(listenerEntered.await(5, TimeUnit.SECONDS), "listener did not start");

            // The listener is still blocked, but the window lock is already
            // released, so a metrics reader must return promptly.
            Future<TrafficMetricsDto> reader = executor.submit(service::getMetrics);
            TrafficMetricsDto snapshot = reader.get(5, TimeUnit.SECONDS);
            assertEquals(1L, snapshot.getWindowSequence());
            assertEquals(1L, snapshot.getWindowRequestCount());

            releaseListener.countDown();
            updater.get(5, TimeUnit.SECONDS);
        } finally {
            releaseListener.countDown();
            executor.shutdownNow();
        }
    }

    private static int requestCountForWindow(int window) {
        return (window % 7) + 1;
    }

    private static final class BlockingTrafficMetrics extends TrafficMetrics {
        private final CountDownLatch windowClosed = new CountDownLatch(1);
        private final CountDownLatch continueUpdate = new CountDownLatch(1);
        private final AtomicBoolean blockNextClose = new AtomicBoolean(true);

        private BlockingTrafficMetrics(MonitoringServiceTest.FixedClock clock) {
            super(clock);
        }

        @Override
        public void closeWindow(Duration windowDuration) {
            super.closeWindow(windowDuration);
            if (blockNextClose.compareAndSet(true, false)) {
                windowClosed.countDown();
                try {
                    if (!continueUpdate.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("timed out waiting to release the window update");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("interrupted while blocking the window update", e);
                }
            }
        }
    }
}
