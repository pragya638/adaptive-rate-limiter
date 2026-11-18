package com.example.ratelimiter.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

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
import com.example.ratelimiter.monitoring.TrafficMetricsDto;
import com.example.ratelimiter.ratelimit.store.CounterStore;
import com.example.ratelimiter.ratelimit.store.InMemoryCounterStore;
import com.example.ratelimiter.ratelimit.store.RedisCounterStore;

class SystemStatusServiceTest {

    private RateLimitProperties rateLimitProperties;
    private RedisRateLimitProperties redisRateLimitProperties;
    private MonitoringProperties monitoringProperties;
    private MonitoringService monitoringService;
    private CompletedWindowStore completedWindowStore;
    private ObjectProvider<CounterStore> counterStoreProvider;
    private MlServiceProperties mlServiceProperties;
    private AnomalyServiceClient anomalyServiceClient;
    private MlEvaluationTracker mlEvaluationTracker;
    private AdaptiveStatusService adaptiveStatusService;
    private SystemStatusService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        rateLimitProperties = new RateLimitProperties();
        rateLimitProperties.setMaxRequests(5);
        rateLimitProperties.setWindow(Duration.ofSeconds(30));
        rateLimitProperties.setStore(RateLimitProperties.StoreType.MEMORY);
        redisRateLimitProperties = new RedisRateLimitProperties();

        monitoringProperties = new MonitoringProperties();
        monitoringProperties.setObservationInterval(Duration.ofSeconds(30));
        monitoringProperties.setEwmaAlpha(0.3);

        monitoringService = mock(MonitoringService.class);
        completedWindowStore = mock(CompletedWindowStore.class);
        counterStoreProvider = mock(ObjectProvider.class);
        mlServiceProperties = new MlServiceProperties();
        anomalyServiceClient = mock(AnomalyServiceClient.class);
        mlEvaluationTracker = new MlEvaluationTracker(
                Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));
        adaptiveStatusService = mock(AdaptiveStatusService.class);

        when(counterStoreProvider.getIfAvailable()).thenReturn(new InMemoryCounterStore());
        when(completedWindowStore.size()).thenReturn(2);
        when(monitoringService.getMetrics()).thenReturn(metrics());
        when(adaptiveStatusService.snapshot()).thenReturn(adaptiveStatus());

        service = new SystemStatusService(rateLimitProperties, redisRateLimitProperties,
                monitoringProperties, monitoringService, completedWindowStore, counterStoreProvider,
                mlServiceProperties, anomalyServiceClient, mlEvaluationTracker, adaptiveStatusService);
    }

    @Test
    @DisplayName("memory store is verified without contacting Redis or ML when ML is disabled")
    void reportsMemoryStoreAndDisabledMl() {
        mlServiceProperties.setEnabled(false);

        SystemStatusDto status = service.snapshot();

        assertThat(status.backend().status()).isEqualTo("UP");
        assertThat(status.rateLimiter().mode()).isEqualTo("ADAPTIVE");
        assertThat(status.rateLimiter().configuredMaxRequests()).isEqualTo(5);
        assertThat(status.rateLimiter().windowSeconds()).isEqualTo(30.0);
        assertThat(status.rateLimiter().store()).isEqualTo("MEMORY");
        assertThat(status.store().type()).isEqualTo("MEMORY");
        assertThat(status.store().available()).isTrue();
        assertThat(status.store().verified()).isTrue();
        assertThat(status.store().status()).isEqualTo("ready");
        assertThat(status.store().fallbackMode()).isNull();
        assertThat(status.monitoring().schedulerStatus()).isEqualTo("scheduled");
        assertThat(status.monitoring().pendingWindows()).isEqualTo(2);
        assertThat(status.monitoring().observationIntervalSeconds()).isEqualTo(30.0);
        assertThat(status.monitoring().lastCompletedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:30Z"));
        assertThat(status.monitoring().latestWindow()).isEqualTo(adaptiveStatus().trafficWindow());
        assertThat(status.ml().enabled()).isFalse();
        assertThat(status.ml().status()).isEqualTo("disabled");
        assertThat(status.ml().lastSuccessfulEvaluationAt()).isNull();
        assertThat(status.ml().lastFailureAt()).isNull();
        assertThat(status.adaptive()).isEqualTo(adaptiveStatus());
    }

    @Test
    @DisplayName("healthy ML reports verified health and model metadata")
    void reportsHealthyMl() throws Exception {
        MlHealthDto health = new MlHealthDto();
        health.setStatus("ok");
        health.setModelReady(true);
        health.setModel("IsolationForest");
        health.setVersion("0.4.0");
        MlModelInfoDto info = new MlModelInfoDto();
        info.setModel("IsolationForest");
        info.setContamination(0.05);
        info.setNEstimators(200);
        info.setRandomState(42);
        info.setFeatureNames(List.of("request_rate", "error_rate", "burst_ratio", "request_count"));
        info.setObservationIntervalSeconds(30.0);
        info.setFitted(true);
        info.setTrainingSamples(2000);
        when(anomalyServiceClient.health()).thenReturn(health);
        when(anomalyServiceClient.modelInfo()).thenReturn(info);
        mlEvaluationTracker.recordSuccess();

        SystemStatusDto status = service.snapshot();

        assertThat(status.ml().enabled()).isTrue();
        assertThat(status.ml().available()).isTrue();
        assertThat(status.ml().modelReady()).isTrue();
        assertThat(status.ml().status()).isEqualTo("ok");
        assertThat(status.ml().model()).isEqualTo("IsolationForest");
        assertThat(status.ml().version()).isEqualTo("0.4.0");
        assertThat(status.ml().latencyMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(status.ml().lastSuccessfulEvaluationAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(status.ml().lastFailureAt()).isNull();
        assertThat(status.ml().detail()).isNull();
        assertThat(status.ml().modelInfo()).isNotNull();
        assertThat(status.ml().modelInfo().nEstimators()).isEqualTo(200);
        assertThat(status.ml().modelInfo().featureNames()).containsExactly(
                "request_rate", "error_rate", "burst_ratio", "request_count");
        assertThat(status.ml().modelInfo().trainingSamples()).isEqualTo(2000);
    }

    @Test
    @DisplayName("unavailable ML is reported honestly without fabricating metadata")
    void reportsUnavailableMl() throws Exception {
        when(anomalyServiceClient.health()).thenThrow(new IOException("connection refused"));
        mlEvaluationTracker.recordFailure("ML anomaly assessment failed (ConnectException).");

        SystemStatusDto status = service.snapshot();

        assertThat(status.ml().available()).isFalse();
        assertThat(status.ml().status()).isEqualTo("unavailable");
        assertThat(status.ml().modelReady()).isFalse();
        assertThat(status.ml().model()).isNull();
        assertThat(status.ml().modelInfo()).isNull();
        assertThat(status.ml().lastSuccessfulEvaluationAt()).isNull();
        assertThat(status.ml().lastFailureAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(status.ml().lastFailureDetail()).contains("ConnectException");
        assertThat(status.ml().detail()).contains("IOException");
        verify(anomalyServiceClient, never()).modelInfo();
    }

    @Test
    @DisplayName("Redis store health is actively verified with a read-only ping")
    void reportsRedisPingFailureAsDegraded() {
        rateLimitProperties.setStore(RateLimitProperties.StoreType.REDIS);
        RedisCounterStore redisStore = mock(RedisCounterStore.class);
        when(redisStore.ping()).thenReturn(false);
        when(counterStoreProvider.getIfAvailable()).thenReturn(redisStore);
        mlServiceProperties.setEnabled(false);

        SystemStatusDto status = service.snapshot();

        assertThat(status.store().type()).isEqualTo("REDIS");
        assertThat(status.store().verified()).isTrue();
        assertThat(status.store().available()).isFalse();
        assertThat(status.store().status()).isEqualTo("degraded");
        assertThat(status.store().fallbackMode()).isEqualTo("OPEN");
        verify(redisStore).ping();
    }

    private AdaptiveStatusDto adaptiveStatus() {
        AdaptiveStatusDto.TrafficWindowStatus window = new AdaptiveStatusDto.TrafficWindowStatus(
                4L,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:30Z"),
                30.0,
                12L,
                11L,
                1L,
                0.02,
                4.0,
                3.5);
        return new AdaptiveStatusDto(
                true,
                "ADAPTIVE",
                10,
                1,
                100,
                5,
                Instant.parse("2026-01-01T10:00:31Z"),
                Instant.parse("2026-01-01T10:00:31Z"),
                new AdaptiveStatusDto.AnomalyStatus(true, false, -0.12, "normal", "IsolationForest"),
                new AdaptiveStatusDto.FeatureVectorStatus(0.4, 0.02, 0.11, 12L),
                window,
                new AdaptiveStatusDto.DecisionStatus("INCREASE_LIMIT", 9, 10, "NORMAL_TRAFFIC",
                        "Normal traffic patterns.", Instant.parse("2026-01-01T10:00:31Z")));
    }

    private TrafficMetricsDto metrics() {
        return new TrafficMetricsDto(
                100L, 90L, 10L, 0.08, 4.0, 3.5, Instant.parse("2026-01-01T10:00:30Z"),
                12L, 11L, 1L, 0.02, 4L,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:30Z"), 30.0);
    }
}
