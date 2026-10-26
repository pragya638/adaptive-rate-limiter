package com.example.ratelimiter.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

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

