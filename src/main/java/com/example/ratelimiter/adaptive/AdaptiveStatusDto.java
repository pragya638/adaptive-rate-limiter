package com.example.ratelimiter.adaptive;

import java.time.Instant;

/**
 * Read-only, non-sensitive snapshot of the adaptive controller.
 *
 * <p>Only operational values are exposed: limits, the latest anomaly
 * assessment, the latest completed traffic window, and adaptation timestamps.
 * ML service URLs, credentials, and other configuration internals are not
 * included.
 */
public record AdaptiveStatusDto(
        boolean enabled,
        String mode,
        int currentLimit,
        int minLimit,
        int maxLimit,
        int initialLimit,
        Instant lastEvaluationAt,
        Instant lastAdaptationAt,
        AnomalyStatus latestAnomaly,
        FeatureVectorStatus latestFeatureVector,
        TrafficWindowStatus trafficWindow,
        DecisionStatus lastDecision) {

    public record AnomalyStatus(
            boolean available,
            Boolean isAnomaly,
            Double anomalyScore,
            String prediction,
            String model) {
    }

    public record FeatureVectorStatus(
            double requestRate,
            double errorRate,
            double burstRatio,
            long requestCount) {
    }

    public record TrafficWindowStatus(
            long sequence,
            Instant start,
            Instant end,
            double durationSeconds,
            long requestCount,
            long allowedRequests,
            long rejectedRequests,
            double clientErrorRate,
            double requestRate,
            double ewmaRequestRate) {
    }

    public record DecisionStatus(
            String action,
            int previousLimit,
            int appliedLimit,
            String reasonCode,
            String explanation,
            Instant evaluatedAt) {
    }
}
