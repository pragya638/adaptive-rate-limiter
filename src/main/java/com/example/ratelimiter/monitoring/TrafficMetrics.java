package com.example.ratelimiter.monitoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

@Component
public class TrafficMetrics {
    private final Clock clock;
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong rejectedByRateLimit = new AtomicLong(0);
    private final AtomicLong otherErrorResponses = new AtomicLong(0);
    private final AtomicLong windowRequests = new AtomicLong(0);
    private final AtomicLong windowStartEpochMillis = new AtomicLong(0);
    private final AtomicReference<Double> currentRequestRatePerSecond = new AtomicReference<>(Double.valueOf(0.0));
    private final AtomicReference<Instant> observationTimestamp = new AtomicReference<>(Instant.now());

    public TrafficMetrics(Clock clock) {
        this.clock = clock;
        this.windowStartEpochMillis.set(clock.millis());
        this.observationTimestamp.set(clock.instant());
    }

    public void recordRequest() {
        totalRequests.incrementAndGet();
        windowRequests.incrementAndGet();
        observationTimestamp.set(clock.instant());
    }

    public void recordAllowed() {
        allowedRequests.incrementAndGet();
    }

    public void recordRejectedByRateLimit() {
        rejectedByRateLimit.incrementAndGet();
    }

    public void recordOtherError() {
        otherErrorResponses.incrementAndGet();
    }

    public void closeWindow(Duration windowDuration) {
        if (windowDuration == null || windowDuration.isZero() || windowDuration.isNegative()) {
            windowDuration = Duration.ofSeconds(1);
        }
        long nowMillis = clock.millis();
        long count = windowRequests.getAndSet(0);
        long intervalMillis = windowDuration.toMillis();
        if (intervalMillis <= 0) {
            intervalMillis = 1000L;
        }
        double rate = (count * 1000.0) / (double) intervalMillis;
        if (Double.isNaN(rate) || Double.isInfinite(rate) || rate < 0.0) {
            rate = 0.0;
        }
        currentRequestRatePerSecond.set(Double.valueOf(rate));
        windowStartEpochMillis.set(nowMillis);
        observationTimestamp.set(clock.instant());
    }

    public long getTotalRequests() { return totalRequests.get(); }
    public long getAllowedRequests() { return allowedRequests.get(); }
    public long getRejectedByRateLimit() { return rejectedByRateLimit.get(); }
    public long getOtherErrorResponses() { return otherErrorResponses.get(); }

    public double getErrorRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0.0;
        }
        double errors = (double) (rejectedByRateLimit.get() + otherErrorResponses.get());
        double rate = errors / (double) total;
        if (rate < 0.0) rate = 0.0;
        if (rate > 1.0) rate = 1.0;
        return rate;
    }

    public double getCurrentRequestRatePerSecond() {
        Double v = currentRequestRatePerSecond.get();
        return v == null ? 0.0 : v.doubleValue();
    }

    public Instant getObservationTimestamp() { return observationTimestamp.get(); }
}
