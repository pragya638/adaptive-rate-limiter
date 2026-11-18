package com.example.ratelimiter.monitoring;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MonitoringServiceTest {

    static class FixedClock extends Clock {
        private Instant now;
        FixedClock(Instant start) { this.now = start; }
        void advance(Duration d) { this.now = now.plus(d); }
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
        public long millis() { return now.toEpochMilli(); }
    }

    static class TestProps extends MonitoringProperties {
        TestProps(double alpha, Duration interval) {
            setEwmaAlpha(alpha);
            setObservationInterval(interval);
        }
    }

    @Test
    void constructorRejectsInvalidAlpha() {
        assertThrows(IllegalArgumentException.class, () -> new MonitoringService(new TrafficMetrics(new FixedClock(Instant.now())), new TestProps(0.0, Duration.ofSeconds(30))));
        assertThrows(IllegalArgumentException.class, () -> new MonitoringService(new TrafficMetrics(new FixedClock(Instant.now())), new TestProps(1.5, Duration.ofSeconds(30))));
    }

    @Test
    void scheduledUpdateDrivesEwmaWithConfiguredInterval() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringProperties props = new TestProps(0.5, Duration.ofSeconds(10));
        MonitoringService service = new MonitoringService(metrics, props);

        // Generate requests in first interval window
        metrics.recordRequest();
        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));
        service.updateObservations();
        assertEquals(0.2, metrics.getCurrentRequestRatePerSecond(), 1e-9); // 2 requests / 10s
        assertEquals(0.2, service.getEwmaCalculator().getSmoothed(), 1e-9);

        // Second interval with different rate
        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));
        service.updateObservations();
        // rate for second window is 1/10 = 0.1
        assertEquals(0.1, metrics.getCurrentRequestRatePerSecond(), 1e-9);
        // EWMA with alpha 0.5: 0.5*0.1 + 0.5*0.2 = 0.15
        assertEquals(0.15, service.getEwmaCalculator().getSmoothed(), 1e-9);
    }

    @Test
    void getMetricsExposesCompletedWindowSnapshot() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringProperties props = new TestProps(0.5, Duration.ofSeconds(10));
        MonitoringService service = new MonitoringService(metrics, props);

        metrics.recordRequest();
        metrics.recordRequest();
        metrics.recordAllowed();
        metrics.recordOtherError();
        metrics.recordRejectedByRateLimit();

        clock.advance(Duration.ofSeconds(10));
        service.updateObservations();
        var dto = service.getMetrics();

        assertEquals(2, dto.getWindowRequestCount());
        assertEquals(1, dto.getWindowAllowedRequests());
        assertEquals(1, dto.getWindowRejectedRequests());
        assertEquals(0.5, dto.getClientErrorRate(), 1e-9);
        assertEquals(1.0, dto.getErrorRate(), 1e-9);
    }

    @Test
    void getMetricsUsesMeasuredWindowDurationForFeatureConsistency() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringProperties props = new TestProps(0.5, Duration.ofSeconds(10));
        MonitoringService service = new MonitoringService(metrics, props);

        for (int i = 0; i < 20; i++) {
            metrics.recordRequest();
        }
        // The scheduler ran late: the window is 25s, not the nominal 10s.
        clock.advance(Duration.ofSeconds(25));
        service.updateObservations();
        var dto = service.getMetrics();

        assertEquals(25.0, dto.getWindowDurationSeconds(), 1e-9);
        assertEquals(20.0 / 25.0, dto.getCurrentRequestRate(), 1e-9);
        assertEquals(dto.getWindowStart(), metrics.getLastWindowStart());
        assertEquals(dto.getWindowEnd(), metrics.getLastWindowEnd());
    }

    @Test
    void updateObservationsPublishesExactCompletedWindowSnapshot() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringProperties props = new TestProps(0.5, Duration.ofSeconds(10));
        CompletedWindowStore store = new CompletedWindowStore();
        MonitoringService service = new MonitoringService(metrics, props, store);

        metrics.recordRequest();
        metrics.recordRequest();
        metrics.recordAllowed();
        metrics.recordOtherError();
        metrics.recordRejectedByRateLimit();
        clock.advance(Duration.ofSeconds(10));

        service.updateObservations();

        TrafficMetricsDto snapshot = store.peekFirst();
        assertEquals(1, store.size());
        assertEquals(1L, snapshot.getWindowSequence());
        assertEquals(2L, snapshot.getWindowRequestCount());
        assertEquals(1L, snapshot.getWindowAllowedRequests());
        assertEquals(1L, snapshot.getWindowRejectedRequests());
        assertEquals(0.5, snapshot.getClientErrorRate(), 1e-9);
        assertEquals(0.2, snapshot.getCurrentRequestRate(), 1e-9);
        assertEquals(0.2, snapshot.getEwmaRequestRate(), 1e-9);
        assertEquals(10.0, snapshot.getWindowDurationSeconds(), 1e-9);
    }

    @Test
    void updateObservationsPublishesWindowsInSequenceOrder() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringProperties props = new TestProps(0.5, Duration.ofSeconds(10));
        CompletedWindowStore store = new CompletedWindowStore();
        MonitoringService service = new MonitoringService(metrics, props, store);

        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));
        service.updateObservations();

        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));
        service.updateObservations();

        assertEquals(2, store.size());
        assertEquals(1L, store.removeFirst().getWindowSequence());
        assertEquals(2L, store.removeFirst().getWindowSequence());
    }

    @Test
    void updateObservationsNotifiesListenersAfterPublishing() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringProperties props = new TestProps(0.5, Duration.ofSeconds(10));
        CompletedWindowStore store = new CompletedWindowStore();
        List<TrafficMetricsDto> notified = new ArrayList<>();
        List<Integer> storeSizeAtNotification = new ArrayList<>();
        CompletedWindowListener listener = window -> {
            notified.add(window);
            storeSizeAtNotification.add(store.size());
        };
        MonitoringService service = new MonitoringService(metrics, props, store, List.of(listener));

        metrics.recordRequest();
        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));
        service.updateObservations();

        assertEquals(1, notified.size());
        assertEquals(1L, notified.get(0).getWindowSequence());
        assertEquals(2L, notified.get(0).getWindowRequestCount());
        // Publication must precede notification: the window is already queued
        // when the listener runs.
        assertEquals(1, storeSizeAtNotification.get(0));
    }

    @Test
    void listenerFailureDoesNotPreventPublicationOrOtherListeners() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringProperties props = new TestProps(0.5, Duration.ofSeconds(10));
        CompletedWindowStore store = new CompletedWindowStore();
        CompletedWindowListener failing = window -> {
            throw new IllegalStateException("boom");
        };
        List<Long> recorded = new ArrayList<>();
        CompletedWindowListener recording = window -> recorded.add(window.getWindowSequence());
        MonitoringService service = new MonitoringService(metrics, props, store, List.of(failing, recording));

        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));

        assertDoesNotThrow(service::updateObservations);
        assertEquals(1, store.size());
        assertEquals(List.of(1L), recorded);
    }

    @Test
    void updateObservationsNotifiesListenersOncePerWindow() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringProperties props = new TestProps(0.5, Duration.ofSeconds(10));
        CompletedWindowStore store = new CompletedWindowStore();
        List<Long> sequences = new ArrayList<>();
        MonitoringService service = new MonitoringService(metrics, props, store,
                List.of(window -> sequences.add(window.getWindowSequence())));

        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));
        service.updateObservations();

        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));
        service.updateObservations();

        assertEquals(List.of(1L, 2L), sequences);
    }

    @Test
    void getMetricsReturnsReadOnlyViewAndDoesNotMutateState() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringProperties props = new TestProps(0.3, Duration.ofSeconds(30));
        MonitoringService service = new MonitoringService(metrics, props);
        metrics.recordRequest();
        metrics.recordAllowed();
        long totalBefore = metrics.getTotalRequests();
        double rateBefore = metrics.getCurrentRequestRatePerSecond();
        double ewmaBefore = service.getEwmaCalculator().getSmoothed();
        var dto = service.getMetrics();
        assertEquals(totalBefore, metrics.getTotalRequests());
        assertEquals(totalBefore, dto.getTotalRequests());
        assertEquals(rateBefore, metrics.getCurrentRequestRatePerSecond(), 1e-9);
        assertEquals(ewmaBefore, service.getEwmaCalculator().getSmoothed(), 1e-9);
        // calling again should not change state
        service.getMetrics();
        assertEquals(totalBefore, metrics.getTotalRequests());
        assertEquals(rateBefore, metrics.getCurrentRequestRatePerSecond(), 1e-9);
        assertEquals(ewmaBefore, service.getEwmaCalculator().getSmoothed(), 1e-9);
    }
}

