package com.example.ratelimiter.monitoring;

import java.util.ArrayDeque;
import java.util.Deque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ordered, bounded buffer of completed monitoring windows waiting for adaptive
 * evaluation.
 *
 * <p>Monitoring publishes a window only after its counters, rate, EWMA, and
 * sequence have been captured together. The adaptive scheduler peeks at the
 * oldest window, evaluates it, and removes it only after the evaluation
 * succeeds. This makes window closure authoritative and preserves retry
 * semantics without allowing evaluation latency to delay window closure.
 */
@Component
public class CompletedWindowStore {

    static final int DEFAULT_MAX_PENDING_WINDOWS = 64;

    private static final Logger log = LoggerFactory.getLogger(CompletedWindowStore.class);

    private final int maxPendingWindows;
    private final Deque<TrafficMetricsDto> windows = new ArrayDeque<>();

    public CompletedWindowStore() {
        this(DEFAULT_MAX_PENDING_WINDOWS);
    }

    public CompletedWindowStore(int maxPendingWindows) {
        if (maxPendingWindows < 1) {
            throw new IllegalArgumentException("maxPendingWindows must be at least 1");
        }
        this.maxPendingWindows = maxPendingWindows;
    }

    public synchronized void publish(TrafficMetricsDto window) {
        if (window == null) {
            throw new IllegalArgumentException("window cannot be null");
        }
        if (windows.size() >= maxPendingWindows) {
            TrafficMetricsDto dropped = windows.removeFirst();
            log.warn("Dropping completed window sequence {} because the evaluation buffer is full",
                    dropped.getWindowSequence());
        }
        windows.addLast(window);
    }

    public synchronized TrafficMetricsDto peekFirst() {
        return windows.peekFirst();
    }

    public synchronized TrafficMetricsDto removeFirst() {
        return windows.pollFirst();
    }

    public synchronized int size() {
        return windows.size();
    }

    public synchronized void clear() {
        windows.clear();
    }
}
