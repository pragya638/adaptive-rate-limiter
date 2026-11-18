package com.example.ratelimiter.monitoring;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final TrafficMetrics trafficMetrics;
    private final EwmaCalculator ewmaCalculator;
    private final MonitoringProperties properties;
    private final CompletedWindowStore completedWindowStore;
    private final List<CompletedWindowListener> completedWindowListeners;

    /**
     * Guards the completed-window counters and the EWMA sample derived from
     * them. The writer publishes both under the write lock; readers take the
     * read lock so a snapshot can never combine a new window sequence with the
     * previous EWMA value.
     */
    private final ReentrantReadWriteLock windowLock = new ReentrantReadWriteLock();

    public MonitoringService(TrafficMetrics trafficMetrics, MonitoringProperties properties) {
        this(trafficMetrics, properties, new CompletedWindowStore(), List.of());
    }

    public MonitoringService(TrafficMetrics trafficMetrics, MonitoringProperties properties,
            CompletedWindowStore completedWindowStore) {
        this(trafficMetrics, properties, completedWindowStore, List.of());
    }

    @Autowired
    public MonitoringService(TrafficMetrics trafficMetrics, MonitoringProperties properties,
            CompletedWindowStore completedWindowStore,
            List<CompletedWindowListener> completedWindowListeners) {
        this.trafficMetrics = trafficMetrics;
        this.properties = properties;
        this.completedWindowStore = completedWindowStore;
        this.completedWindowListeners = completedWindowListeners == null
                ? List.of()
                : List.copyOf(completedWindowListeners);
        double alpha = properties.getEwmaAlpha();
        if (alpha <= 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("monitoring.ewma-alpha must be in (0, 1]");
        }
        this.ewmaCalculator = new EwmaCalculator(alpha);
    }

    /**
     * Closes the current observation window and publishes the exact completed
     * snapshot, then hands it off to the registered listeners.
     *
     * <p>The schedule uses a <em>fixed rate</em> (anchored to the configured
     * observation interval) rather than a fixed delay, so a slow listener or ML
     * call cannot progressively stretch later windows. The actual window length
     * is still measured and published with each snapshot, so traffic-rate
     * features keep their meaning even if a tick runs late.
     *
     * <p>Listeners are notified only after the write lock is released and only
     * after the snapshot has been queued. This makes evaluation happen strictly
     * after publication without blocking metrics readers or request handling.
     */
    @Scheduled(initialDelayString = "${monitoring.observation-interval:30s}",
            fixedRateString = "${monitoring.observation-interval:30s}")
    public void updateObservations() {
        Duration interval = properties.getObservationInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            interval = Duration.ofSeconds(30);
        }
        TrafficMetricsDto published;
        windowLock.writeLock().lock();
        try {
            // Publish the completed window and the EWMA sample derived from its
            // rate as one critical section. Without this, a reader can observe
            // the new window sequence/counters while EWMA still holds the value
            // from the previous window.
            trafficMetrics.closeWindow(interval);
            ewmaCalculator.update(trafficMetrics.getCurrentRequestRatePerSecond());
            // Publish the exact snapshot while the write lock is held so the
            // store cannot receive windows out of sequence order.
            published = buildSnapshotLocked();
            completedWindowStore.publish(published);
        } finally {
            windowLock.writeLock().unlock();
        }
        // Hand off outside the lock: evaluation runs after publication, cannot
        // block getMetrics() readers, and a failing listener never prevents the
        // window from having been published.
        notifyWindowPublished(published);
    }

    private void notifyWindowPublished(TrafficMetricsDto window) {
        for (CompletedWindowListener listener : completedWindowListeners) {
            try {
                listener.onWindowPublished(window);
            } catch (RuntimeException ex) {
                log.warn("Completed-window listener {} failed for window {}; the window stays queued for retry",
                        listener.getClass().getSimpleName(), window.getWindowSequence(), ex);
            }
        }
    }

    public TrafficMetricsDto getMetrics() {
        windowLock.readLock().lock();
        try {
            return buildSnapshotLocked();
        } finally {
            windowLock.readLock().unlock();
        }
    }

    private TrafficMetricsDto buildSnapshotLocked() {
        Duration interval = properties.getObservationInterval();
        double configuredIntervalSeconds = interval == null ? 0.0 : interval.toMillis() / 1000.0;
        // Prefer the measured window length so the DTO describes the window
        // that actually produced the counts (schedulers can run late).
        double measuredIntervalSeconds = trafficMetrics.getLastWindowDurationSeconds();
        double intervalSeconds = measuredIntervalSeconds > 0.0
                ? measuredIntervalSeconds
                : configuredIntervalSeconds;
        return new TrafficMetricsDto(
                trafficMetrics.getTotalRequests(),
                trafficMetrics.getAllowedRequests(),
                trafficMetrics.getRejectedByRateLimit(),
                trafficMetrics.getErrorRate(),
                trafficMetrics.getCurrentRequestRatePerSecond(),
                ewmaCalculator.getSmoothed(),
                trafficMetrics.getObservationTimestamp(),
                trafficMetrics.getLastWindowRequestCount(),
                trafficMetrics.getLastWindowAllowedRequests(),
                trafficMetrics.getLastWindowRejectedByRateLimit(),
                trafficMetrics.getLastWindowClientErrorRate(),
                trafficMetrics.getWindowSequence(),
                trafficMetrics.getLastWindowStart(),
                trafficMetrics.getLastWindowEnd(),
                intervalSeconds
        );
    }

    public EwmaCalculator getEwmaCalculator() {
        return ewmaCalculator;
    }

    public TrafficMetrics getTrafficMetrics() {
        return trafficMetrics;
    }
}
