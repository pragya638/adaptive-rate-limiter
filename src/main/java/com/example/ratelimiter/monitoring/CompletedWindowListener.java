package com.example.ratelimiter.monitoring;

/**
 * Callback invoked by {@link MonitoringService} immediately after a completed
 * observation window has been published to the {@link CompletedWindowStore}.
 *
 * <p>This is the deterministic handoff between window publication and adaptive
 * evaluation. The notification happens only after the window snapshot has been
 * captured and queued, and it is delivered <em>outside</em> the monitoring
 * write lock, so:
 *
 * <ul>
 *   <li>a listener always observes a fully captured, already-published window;</li>
 *   <li>listeners never block {@link MonitoringService#getMetrics()} readers or
 *       incoming request handling on the write lock.</li>
 * </ul>
 *
 * <p>Listeners must be non-blocking with respect to the request path. A
 * listener that fails does not prevent the window from being published, and the
 * published window stays queued for the next evaluation attempt.
 */
@FunctionalInterface
public interface CompletedWindowListener {

    /**
     * Called after {@code window} has been published.
     *
     * @param window the completed window snapshot that was just published
     */
    void onWindowPublished(TrafficMetricsDto window);
}
