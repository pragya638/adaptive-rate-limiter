package com.example.ratelimiter.observability;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.example.ratelimiter.adaptive.AdaptiveStatusDto;
import com.example.ratelimiter.adaptive.AdaptiveStatusService;
import com.example.ratelimiter.adaptive.MlServiceProperties;
import com.example.ratelimiter.adaptive.client.AnomalyServiceClient;
import com.example.ratelimiter.adaptive.client.MlHealthDto;
import com.example.ratelimiter.adaptive.client.MlModelInfoDto;
import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.config.RedisRateLimitProperties;
import com.example.ratelimiter.monitoring.CompletedWindowStore;
import com.example.ratelimiter.monitoring.MonitoringProperties;
import com.example.ratelimiter.monitoring.MonitoringService;
import com.example.ratelimiter.ratelimit.store.CounterStore;
import com.example.ratelimiter.ratelimit.store.RedisCounterStore;

@Service
public class SystemStatusService {

    private static final String SERVICE_NAME = "adaptive-rate-limiter";

    private static final String SCHEDULER_STATUS_SCHEDULED = "scheduled";
    private static final String SCHEDULER_STATUS_UNAVAILABLE = "unavailable";

    private final RateLimitProperties rateLimitProperties;
    private final RedisRateLimitProperties redisRateLimitProperties;
    private final MonitoringProperties monitoringProperties;
    private final MonitoringService monitoringService;
    private final CompletedWindowStore completedWindowStore;
    private final ObjectProvider<CounterStore> counterStoreProvider;
    private final MlServiceProperties mlServiceProperties;
    private final AnomalyServiceClient anomalyServiceClient;
    private final MlEvaluationTracker mlEvaluationTracker;
    private final AdaptiveStatusService adaptiveStatusService;

    public SystemStatusService(RateLimitProperties rateLimitProperties,
            RedisRateLimitProperties redisRateLimitProperties,
            MonitoringProperties monitoringProperties,
            MonitoringService monitoringService,
            CompletedWindowStore completedWindowStore,
            ObjectProvider<CounterStore> counterStoreProvider,
            MlServiceProperties mlServiceProperties,
            AnomalyServiceClient anomalyServiceClient,
            MlEvaluationTracker mlEvaluationTracker,
            AdaptiveStatusService adaptiveStatusService) {
        this.rateLimitProperties = rateLimitProperties;
        this.redisRateLimitProperties = redisRateLimitProperties;
        this.monitoringProperties = monitoringProperties;
        this.monitoringService = monitoringService;
        this.completedWindowStore = completedWindowStore;
        this.counterStoreProvider = counterStoreProvider;
        this.mlServiceProperties = mlServiceProperties;
        this.anomalyServiceClient = anomalyServiceClient;
        this.mlEvaluationTracker = mlEvaluationTracker;
        this.adaptiveStatusService = adaptiveStatusService;
    }

    public SystemStatusDto snapshot() {
        AdaptiveStatusDto adaptive = adaptiveStatusService.snapshot();
        return new SystemStatusDto(
                Instant.now(),
                new SystemStatusDto.BackendStatus("UP", SERVICE_NAME),
                rateLimiterStatus(adaptive),
                storeStatus(),
                monitoringStatus(adaptive),
                mlStatus(),
                adaptive);
    }

    private SystemStatusDto.RateLimiterStatus rateLimiterStatus(AdaptiveStatusDto adaptive) {
        long windowMillis = rateLimitProperties.getWindow().toMillis();
        return new SystemStatusDto.RateLimiterStatus(
                rateLimitProperties.isEnabled(),
                adaptive.mode(),
                rateLimitProperties.getMaxRequests(),
                adaptive.currentLimit(),
                windowMillis,
                windowMillis / 1000.0,
                rateLimitProperties.getStore().name(),
                copy(rateLimitProperties.getIncludePaths()),
                copy(rateLimitProperties.getExcludePaths()));
    }

    private SystemStatusDto.StoreStatus storeStatus() {
        CounterStore store = counterStoreProvider.getIfAvailable();
        RateLimitProperties.StoreType configuredType = rateLimitProperties.getStore();
        String fallbackMode = configuredType == RateLimitProperties.StoreType.REDIS
                ? redisRateLimitProperties.getFailureMode().name()
                : null;
        if (store == null) {
            return new SystemStatusDto.StoreStatus(configuredType.name(), false, false, "unknown",
                    "No counter store bean is available.", fallbackMode);
        }
        if (configuredType == RateLimitProperties.StoreType.MEMORY) {
            return new SystemStatusDto.StoreStatus("MEMORY", true, true, "ready",
                    "In-process counter store; no external dependency.", null);
        }
        if (store instanceof RedisCounterStore redisStore) {
            boolean available = redisStore.ping();
            return new SystemStatusDto.StoreStatus("REDIS", available, true,
                    available ? "ready" : "degraded",
                    available
                            ? "Redis PING succeeded."
                            : "Redis PING failed or timed out; the limiter is following its configured failure mode.",
                    fallbackMode);
        }
        return new SystemStatusDto.StoreStatus(configuredType.name(), false, false, "unknown",
                "The configured store type does not match the active counter store.", fallbackMode);
    }

    private SystemStatusDto.MonitoringStatus monitoringStatus(AdaptiveStatusDto adaptive) {
        AdaptiveStatusDto.TrafficWindowStatus latestWindow = adaptive.trafficWindow();
        return new SystemStatusDto.MonitoringStatus(
                monitoringService == null ? SCHEDULER_STATUS_UNAVAILABLE : SCHEDULER_STATUS_SCHEDULED,
                monitoringProperties.getObservationInterval().toMillis() / 1000.0,
                monitoringProperties.getEwmaAlpha(),
                completedWindowStore.size(),
                latestWindow == null ? null : latestWindow.end(),
                latestWindow);
    }

    private SystemStatusDto.MlStatus mlStatus() {
        Instant checkedAt = Instant.now();
        if (!mlServiceProperties.isEnabled()) {
            return new SystemStatusDto.MlStatus(false, checkedAt, false, false, "disabled",
                    null, null, null,
                    mlEvaluationTracker.getLastSuccessAt(),
                    mlEvaluationTracker.getLastFailureAt(),
                    mlEvaluationTracker.getLastFailureDetail(),
                    "ML service integration is disabled by configuration.", null);
        }

        long startedAt = System.nanoTime();
        MlHealthDto health;
        try {
            health = anomalyServiceClient.health();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return mlUnavailable(checkedAt, startedAt, "ML health check was interrupted.");
        } catch (IOException | RuntimeException ex) {
            return mlUnavailable(checkedAt, startedAt,
                    "ML health check failed (" + ex.getClass().getSimpleName() + ").");
        }

        if (health == null || health.getStatus() == null || health.getStatus().isBlank()) {
            return mlUnavailable(checkedAt, startedAt, "ML service returned an invalid health response.");
        }

        long latencyMs = elapsedMillis(startedAt);
        MlModelInfoDto modelInfo = null;
        String modelInfoDetail = null;
        try {
            modelInfo = anomalyServiceClient.modelInfo();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            modelInfoDetail = "ML model metadata request was interrupted.";
        } catch (IOException | RuntimeException ex) {
            modelInfoDetail = "ML model metadata unavailable ("
                    + ex.getClass().getSimpleName() + ").";
        }

        return new SystemStatusDto.MlStatus(
                true,
                checkedAt,
                true,
                health.isModelReady(),
                health.getStatus(),
                health.getModel(),
                health.getVersion(),
                latencyMs,
                mlEvaluationTracker.getLastSuccessAt(),
                mlEvaluationTracker.getLastFailureAt(),
                mlEvaluationTracker.getLastFailureDetail(),
                modelInfoDetail,
                modelInfo(modelInfo));
    }

    private SystemStatusDto.MlStatus mlUnavailable(Instant checkedAt, long startedAt, String detail) {
        return new SystemStatusDto.MlStatus(
                true,
                checkedAt,
                false,
                false,
                "unavailable",
                null,
                null,
                elapsedMillis(startedAt),
                mlEvaluationTracker.getLastSuccessAt(),
                mlEvaluationTracker.getLastFailureAt(),
                mlEvaluationTracker.getLastFailureDetail(),
                detail,
                null);
    }

    private SystemStatusDto.MlModelInfo modelInfo(MlModelInfoDto info) {
        if (info == null) {
            return null;
        }
        return new SystemStatusDto.MlModelInfo(
                info.getModel(),
                info.getContamination(),
                info.getNEstimators(),
                info.getRandomState(),
                info.getFeatureNames() == null ? null : List.copyOf(info.getFeatureNames()),
                info.getObservationIntervalSeconds(),
                info.isFitted(),
                info.getTrainingSamples());
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
