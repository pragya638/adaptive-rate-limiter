package com.example.ratelimiter.history;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Configuration for the bounded, in-memory dashboard history.
 *
 * <p>The limit applies independently to traffic windows, anomaly assessments,
 * adaptive decisions, and limit observations. History is intentionally
 * memory-only: it survives browser reloads while the backend is running, but it
 * does not survive a backend restart.
 */
@Validated
@ConfigurationProperties(prefix = "dashboard-history")
public class DashboardHistoryProperties {

    @Min(1)
    @Max(10_000)
    private int maxRecords = 120;

    public int getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(int maxRecords) {
        this.maxRecords = maxRecords;
    }
}
