package com.example.ratelimiter.history;

import java.time.Instant;
import java.util.List;

/**
 * Read-only snapshot of retained dashboard history.
 *
 * <p>All lists are ordered oldest to newest. Only operational metrics and
 * decision explanations are exposed; service URLs, credentials, and other
 * configuration internals are not included.
 */
public record DashboardHistoryDto(
        Instant generatedAt,
        String storage,
        boolean survivesRestart,
        int maxRecords,
        List<TrafficRecord> traffic,
        List<AnomalyRecord> anomalies,
        List<DecisionRecord> decisions,
        List<LimitRecord> limits) {

    public record TrafficRecord(
            long windowSequence,
            Instant windowStart,
            Instant windowEnd,
            long requestCount,
            long allowedRequests,
            long rejectedRequests,
            double clientErrorRate,
            double requestRate,
            double ewmaRequestRate,
            double errorRate) {
    }

    public record AnomalyRecord(
            long windowSequence,
            Instant evaluatedAt,
            boolean available,
            Boolean isAnomaly,
            Double anomalyScore,
            String prediction,
            String model) {
    }

    public record DecisionRecord(
            long windowSequence,
            Instant evaluatedAt,
            String action,
            int previousLimit,
            int proposedLimit,
            int appliedLimit,
            String reasonCode,
            String explanation) {
    }

    public record LimitRecord(
            long windowSequence,
            Instant evaluatedAt,
            int previousLimit,
            int limit,
            String action) {
    }
}
