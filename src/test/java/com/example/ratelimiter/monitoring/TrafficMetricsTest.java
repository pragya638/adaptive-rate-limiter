package com.example.ratelimiter.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class TrafficMetricsTest {

    static class FixedClock extends Clock {
        private Instant now;
        FixedClock(Instant start) { this.now = start; }
        void advance(Duration d) { this.now = now.plus(d); }
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
        public long millis() { return now.toEpochMilli(); }
    }

    @Test
    void countingAndErrorRate() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        metrics.recordRequest();
        metrics.recordRequest();
        metrics.recordAllowed();
        metrics.recordRejectedByRateLimit();
        assertEquals(2, metrics.getTotalRequests());
        assertEquals(1, metrics.getAllowedRequests());
        assertEquals(1, metrics.getRejectedByRateLimit());
        assertEquals((1.0)/2.0, metrics.getErrorRate(), 1e-9);
    }

    @Test
    void rateCalculationUsesMeasuredElapsedWindow() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        metrics.recordRequest();
        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(2));
        // The window actually lasted 2s even though the nominal duration is 1s.
        metrics.closeWindow(Duration.ofSeconds(1));
        assertEquals(1.0, metrics.getCurrentRequestRatePerSecond(), 1e-9);
        assertEquals(2.0, metrics.getLastWindowDurationSeconds(), 1e-9);
    }

    @Test
    void completedWindowDurationReflectsActualSchedulerTiming() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        recordBatch(metrics, 10, 10, 0, 0);

        clock.advance(Duration.ofSeconds(45));
        metrics.closeWindow(Duration.ofSeconds(30));

        assertEquals(45.0, metrics.getLastWindowDurationSeconds(), 1e-9);
        assertEquals(10.0 / 45.0, metrics.getCurrentRequestRatePerSecond(), 1e-9);
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), metrics.getLastWindowStart());
        assertEquals(Instant.parse("2025-01-01T00:00:45Z"), metrics.getLastWindowEnd());
    }

    @Test
    void getMetricsReadOnlyDoesNotResetOrMutateCounters() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        metrics.recordRequest();
        metrics.recordAllowed();
        long totalBefore = metrics.getTotalRequests();
        long allowedBefore = metrics.getAllowedRequests();
        double rateBefore = metrics.getCurrentRequestRatePerSecond();
        metrics.getTotalRequests();
        metrics.getAllowedRequests();
        metrics.getRejectedByRateLimit();
        metrics.getOtherErrorResponses();
        metrics.getErrorRate();
        metrics.getCurrentRequestRatePerSecond();
        metrics.getObservationTimestamp();
        assertEquals(totalBefore, metrics.getTotalRequests());
        assertEquals(allowedBefore, metrics.getAllowedRequests());
        assertEquals(rateBefore, metrics.getCurrentRequestRatePerSecond(), 1e-9);
    }

    @Test
    void zeroRequestIntervalProducesZeroRateAndPreservesBehaviour() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        clock.advance(Duration.ofSeconds(30));
        metrics.closeWindow(Duration.ofSeconds(30));
        assertEquals(0.0, metrics.getCurrentRequestRatePerSecond(), 1e-9);
    }

    @Test
    void completedWindowExposesCountsAndExcludesRateLimitRejectionsFromClientErrorRate() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        recordBatch(metrics, 4, 2, 2, 1);

        clock.advance(Duration.ofSeconds(1));
        metrics.closeWindow(Duration.ofSeconds(1));

        assertEquals(4, metrics.getLastWindowRequestCount());
        assertEquals(2, metrics.getLastWindowAllowedRequests());
        assertEquals(2, metrics.getLastWindowRejectedByRateLimit());
        assertEquals(1, metrics.getLastWindowOtherErrorResponses());
        assertEquals(0.25, metrics.getLastWindowClientErrorRate(), 1e-9);
        assertEquals(0.75, metrics.getErrorRate(), 1e-9);
        assertEquals(0.25, metrics.getClientErrorRate(), 1e-9);
    }

    @Test
    void windowCountsResetBetweenObservations() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        recordBatch(metrics, 4, 2, 2, 1);
        clock.advance(Duration.ofSeconds(1));
        metrics.closeWindow(Duration.ofSeconds(1));

        recordBatch(metrics, 2, 1, 1, 0);
        clock.advance(Duration.ofSeconds(1));
        metrics.closeWindow(Duration.ofSeconds(1));

        assertEquals(2, metrics.getLastWindowRequestCount());
        assertEquals(1, metrics.getLastWindowAllowedRequests());
        assertEquals(1, metrics.getLastWindowRejectedByRateLimit());
        assertEquals(0.0, metrics.getLastWindowClientErrorRate(), 1e-9);
        assertEquals(6, metrics.getTotalRequests());
    }

    private void recordBatch(TrafficMetrics metrics, int requests, int allowed, int rejected, int otherErrors) {
        for (int i = 0; i < requests; i++) {
            metrics.recordRequest();
        }
        for (int i = 0; i < allowed; i++) {
            metrics.recordAllowed();
        }
        for (int i = 0; i < rejected; i++) {
            metrics.recordRejectedByRateLimit();
        }
        for (int i = 0; i < otherErrors; i++) {
            metrics.recordOtherError();
        }
    }
}