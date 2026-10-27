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
    void rateCalculationAndZeroInterval() {
        FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        metrics.recordRequest();
        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(2));
        metrics.closeWindow(Duration.ofSeconds(1));
        assertEquals(2.0, metrics.getCurrentRequestRatePerSecond(), 1e-9);
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
}