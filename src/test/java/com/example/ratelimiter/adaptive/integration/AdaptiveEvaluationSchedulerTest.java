package com.example.ratelimiter.adaptive.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.ratelimiter.adaptive.AdaptiveDecisionEngine;
import com.example.ratelimiter.adaptive.AdaptiveEngineProperties;
import com.example.ratelimiter.adaptive.AdaptiveIntegrationProperties;
import com.example.ratelimiter.adaptive.AdaptiveLimitAdvisor;
import com.example.ratelimiter.adaptive.AnomalyAssessment;
import com.example.ratelimiter.adaptive.AnomalyAssessmentProvider;
import com.example.ratelimiter.monitoring.MonitoringService;
import com.example.ratelimiter.monitoring.TrafficMetricsDto;
import com.example.ratelimiter.ratelimit.DynamicRateLimiter;

class AdaptiveEvaluationSchedulerTest {

    private MonitoringService monitoringService;
    private AnomalyAssessmentProvider anomalyProvider;
    private DynamicRateLimiter limiter;
    private AdaptiveEvaluationScheduler scheduler;

    @BeforeEach
    void setUp() {
        AdaptiveEngineProperties engineProperties = new AdaptiveEngineProperties();
        engineProperties.setMinLimit(1);
        engineProperties.setMaxLimit(1000);
        engineProperties.setInitialLimit(100);
        engineProperties.setIncreaseStep(10);
        engineProperties.setDecreaseStep(10);

        AdaptiveIntegrationProperties integrationProperties = new AdaptiveIntegrationProperties();
        integrationProperties.setCooldownSeconds(0);

        Clock clock = Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC);
        limiter = new DynamicRateLimiter(100, 1, 1000, Duration.ofSeconds(30), clock);
        AdaptiveDecisionEngine engine = new AdaptiveDecisionEngine(engineProperties, clock);
        AdaptiveLimitAdvisor advisor = new AdaptiveLimitAdvisor(engine, limiter, integrationProperties, clock);

        monitoringService = mock(MonitoringService.class);
        anomalyProvider = mock(AnomalyAssessmentProvider.class);
        when(monitoringService.getMetrics()).thenReturn(new TrafficMetricsDto(
                1000L, 990L, 10L, 0.01, 60.0, 55.0, Instant.now(clock)));

        scheduler = new AdaptiveEvaluationScheduler(monitoringService, anomalyProvider, advisor);
    }

    @Test
    @DisplayName("scheduled evaluation applies an anomalous decision to the limiter")
    void scheduledEvaluationAppliesDecision() {
        when(anomalyProvider.assess(any())).thenReturn(new AnomalyAssessment(true, 0.8, "anomaly", "IsolationForest"));

        scheduler.evaluate();

        assertThat(limiter.getCurrentLimit()).isEqualTo(90);
    }

    @Test
    @DisplayName("scheduled evaluation keeps the limit when the anomaly service is unavailable")
    void unavailableAnomalyKeepsLimit() {
        when(anomalyProvider.assess(any())).thenReturn(AnomalyAssessment.unavailable());

        scheduler.evaluate();

        assertThat(limiter.getCurrentLimit()).isEqualTo(100);
    }

    @Test
    @DisplayName("scheduled evaluation does not propagate unexpected provider failures")
    void providerFailureDoesNotPropagate() {
        when(anomalyProvider.assess(any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(scheduler::evaluate).doesNotThrowAnyException();
        assertThat(limiter.getCurrentLimit()).isEqualTo(100);
    }
}
