package com.example.ratelimiter.observability;

import java.time.Instant;
import java.util.List;

import com.example.ratelimiter.adaptive.AdaptiveStatusDto;

public record SystemStatusDto(
        Instant timestamp,
        BackendStatus backend,
        RateLimiterStatus rateLimiter,
        StoreStatus store,
        MonitoringStatus monitoring,
        MlStatus ml,
        AdaptiveStatusDto adaptive) {

    public record BackendStatus(String status, String service) {
    }

    public record RateLimiterStatus(
            boolean enabled,
            String mode,
            int configuredMaxRequests,
            int activeLimit,
            long windowMillis,
            double windowSeconds,
            String store,
            List<String> includePaths,
            List<String> excludePaths) {
    }

    public record StoreStatus(
            String type,
            boolean available,
            boolean verified,
            String status,
            String detail,
            String fallbackMode) {
    }

    public record MonitoringStatus(
            String schedulerStatus,
            double observationIntervalSeconds,
            double ewmaAlpha,
            int pendingWindows,
            Instant lastCompletedAt,
            AdaptiveStatusDto.TrafficWindowStatus latestWindow) {
    }

    public record MlStatus(
            boolean enabled,
            Instant checkedAt,
            boolean available,
            boolean modelReady,
            String status,
            String model,
            String version,
            Long latencyMs,
            Instant lastSuccessfulEvaluationAt,
            Instant lastFailureAt,
            String lastFailureDetail,
            String detail,
            MlModelInfo modelInfo) {
    }

    public record MlModelInfo(
            String model,
            double contamination,
            int nEstimators,
            int randomState,
            List<String> featureNames,
            double observationIntervalSeconds,
            boolean fitted,
            int trainingSamples) {
    }
}
