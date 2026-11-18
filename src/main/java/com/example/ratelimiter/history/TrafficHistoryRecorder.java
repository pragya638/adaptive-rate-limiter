package com.example.ratelimiter.history;

import org.springframework.stereotype.Component;

import com.example.ratelimiter.monitoring.CompletedWindowListener;
import com.example.ratelimiter.monitoring.TrafficMetricsDto;

/**
 * Captures every completed monitoring window for the dashboard regardless of
 * whether adaptive evaluation is enabled.
 *
 * <p>Binding happens through {@link CompletedWindowListener}, so recording
 * occurs after the window snapshot is published and outside the monitoring
 * write lock.
 */
@Component
public class TrafficHistoryRecorder implements CompletedWindowListener {

    private final DashboardHistoryService historyService;

    public TrafficHistoryRecorder(DashboardHistoryService historyService) {
        this.historyService = historyService;
    }

    @Override
    public void onWindowPublished(TrafficMetricsDto window) {
        historyService.recordTrafficWindow(window);
    }
}
