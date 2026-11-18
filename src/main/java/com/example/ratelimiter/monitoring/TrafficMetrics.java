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
    private final AtomicLong windowAllowedRequests = new AtomicLong(0);
    private final AtomicLong windowRejectedByRateLimit = new AtomicLong(0);
    private final AtomicLong windowOtherErrorResponses = new AtomicLong(0);
    private final AtomicLong lastWindowRequests = new AtomicLong(0);
    private final AtomicLong lastWindowAllowedRequests = new AtomicLong(0);
    private final AtomicLong lastWindowRejectedByRateLimit = new AtomicLong(0);
    private final AtomicLong lastWindowOtherErrorResponses = new AtomicLong(0);
    private final AtomicLong windowStartEpochMillis = new AtomicLong(0);
    private final AtomicLong windowSequence = new AtomicLong(0);
    private final AtomicLong lastWindowStartEpochMillis = new AtomicLong(0);
    private final AtomicLong lastWindowEndEpochMillis = new AtomicLong(0);
    private final AtomicLong lastWindowDurationMillis = new AtomicLong(0);
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
        windowAllowedRequests.incrementAndGet();
    }

    public void recordRejectedByRateLimit() {
        rejectedByRateLimit.incrementAndGet();
        windowRejectedByRateLimit.incrementAndGet();
    }

    public void recordOtherError() {
        otherErrorResponses.incrementAndGet();
        windowOtherErrorResponses.incrementAndGet();
    }

    public void closeWindow(Duration windowDuration) {
        if (windowDuration == null || windowDuration.isZero() || windowDuration.isNegative()) {
            windowDuration = Duration.ofSeconds(1);
        }
        long nowMillis = clock.millis();
        long windowStart = windowStartEpochMillis.get();
        long count = windowRequests.getAndSet(0);
        long allowed = windowAllowedRequests.getAndSet(0);
        long rejected = windowRejectedByRateLimit.getAndSet(0);
        long otherErrors = windowOtherErrorResponses.getAndSet(0);
        lastWindowRequests.set(count);
        lastWindowAllowedRequests.set(allowed);
        lastWindowRejectedByRateLimit.set(rejected);
        lastWindowOtherErrorResponses.set(otherErrors);
        long nominalIntervalMillis = windowDuration.toMillis();
        if (nominalIntervalMillis <= 0) {
            nominalIntervalMillis = 1000L;
        }
        // Use the actual elapsed time between window boundaries. A fixed-delay
        // scheduler is not exact: closing late must not inflate the reported
        // request rate. If the clock moved backwards or the window has not
        // advanced, fall back to the configured nominal interval.
        long elapsedMillis = nowMillis - windowStart;
        if (elapsedMillis <= 0) {
            elapsedMillis = nominalIntervalMillis;
        }
        double rate = (count * 1000.0) / (double) elapsedMillis;
        if (Double.isNaN(rate) || Double.isInfinite(rate) || rate < 0.0) {
            rate = 0.0;
        }
        currentRequestRatePerSecond.set(Double.valueOf(rate));
        lastWindowStartEpochMillis.set(windowStart);
        lastWindowEndEpochMillis.set(nowMillis);
        lastWindowDurationMillis.set(elapsedMillis);
        windowStartEpochMillis.set(nowMillis);
        observationTimestamp.set(clock.instant());
        // Publish the sequence last so readers that observe a new sequence also
        // see the matching window counts, rates, and timestamps.
        windowSequence.incrementAndGet();
    }

    public long getTotalRequests() { return totalRequests.get(); }
    public long getAllowedRequests() { return allowedRequests.get(); }
    public long getRejectedByRateLimit() { return rejectedByRateLimit.get(); }
    public long getOtherErrorResponses() { return otherErrorResponses.get(); }

    public long getLastWindowRequestCount() { return lastWindowRequests.get(); }
    public long getLastWindowAllowedRequests() { return lastWindowAllowedRequests.get(); }
    public long getLastWindowRejectedByRateLimit() { return lastWindowRejectedByRateLimit.get(); }
    public long getLastWindowOtherErrorResponses() { return lastWindowOtherErrorResponses.get(); }

    public long getWindowSequence() { return windowSequence.get(); }

    public Instant getLastWindowStart() {
        long millis = lastWindowStartEpochMillis.get();
        return millis == 0L ? null : Instant.ofEpochMilli(millis);
    }

    public Instant getLastWindowEnd() {
        long millis = lastWindowEndEpochMillis.get();
        return millis == 0L ? null : Instant.ofEpochMilli(millis);
    }

    /**
     * Actual length of the most recently completed observation window. This is
     * measured from the previous window boundary to the close time, so it can
     * differ from the configured nominal interval when the scheduler runs late.
     */
    public double getLastWindowDurationSeconds() {
        long millis = lastWindowDurationMillis.get();
        return millis <= 0L ? 0.0 : millis / 1000.0;
    }

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

    /**
     * Cumulative client/server error rate. Rate-limit rejections are excluded
     * so the adaptive controller does not react to its own throttling.
     */
    public double getClientErrorRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0.0;
        }
        double rate = (double) otherErrorResponses.get() / (double) total;
        if (rate < 0.0) rate = 0.0;
        if (rate > 1.0) rate = 1.0;
        return rate;
    }

    /**
     * Client/server error rate for the most recently completed observation
     * window, excluding rate-limit rejections.
     */
    public double getLastWindowClientErrorRate() {
        long total = lastWindowRequests.get();
        if (total == 0) {
            return 0.0;
        }
        double rate = (double) lastWindowOtherErrorResponses.get() / (double) total;
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
