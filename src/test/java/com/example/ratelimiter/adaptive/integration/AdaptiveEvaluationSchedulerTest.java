package com.example.ratelimiter.adaptive.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.ratelimiter.adaptive.AdaptiveDecisionEngine;
import com.example.ratelimiter.adaptive.AdaptiveEngineProperties;
import com.example.ratelimiter.adaptive.AdaptiveIntegrationProperties;
import com.example.ratelimiter.adaptive.AdaptiveLimitAdvisor;
import com.example.ratelimiter.adaptive.AnomalyAssessment;
import com.example.ratelimiter.adaptive.AnomalyAssessmentProvider;
import com.example.ratelimiter.adaptive.TrafficAssessment;
import com.example.ratelimiter.adaptive.lock.NoopAdaptiveEvaluationLock;
import com.example.ratelimiter.history.DashboardHistoryProperties;
import com.example.ratelimiter.history.DashboardHistoryService;
import com.example.ratelimiter.history.DashboardHistoryStore;
import com.example.ratelimiter.monitoring.CompletedWindowStore;
import com.example.ratelimiter.monitoring.MonitoringProperties;
import com.example.ratelimiter.monitoring.MonitoringService;
import com.example.ratelimiter.monitoring.TrafficMetrics;
import com.example.ratelimiter.monitoring.TrafficMetricsDto;
import com.example.ratelimiter.ratelimit.DynamicRateLimiter;

class AdaptiveEvaluationSchedulerTest {

    private CompletedWindowStore completedWindowStore;
    private AnomalyAssessmentProvider anomalyProvider;
    private DynamicRateLimiter limiter;
    private AdaptiveEvaluationScheduler scheduler;
    private DashboardHistoryService historyService;

    @BeforeEach
    void setUp() {
        AdaptiveEngineProperties engineProperties = new AdaptiveEngineProperties();
        engineProperties.setMinLimit(1);
        engineProperties.setMaxLimit(1000);
        engineProperties.setInitialLimit(100);
        engineProperties.setIncreaseStep(10);
        engineProperties.setDecreaseStep(10);
        engineProperties.setAnomalyConfirmationWindows(1);
        engineProperties.setGrowthConfirmationWindows(1);

        AdaptiveIntegrationProperties integrationProperties = new AdaptiveIntegrationProperties();
        integrationProperties.setCooldownSeconds(0);

        Clock clock = Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC);
        limiter = new DynamicRateLimiter(100, 1, 1000, Duration.ofSeconds(30), clock);
        AdaptiveDecisionEngine engine = new AdaptiveDecisionEngine(engineProperties, clock);
        AdaptiveLimitAdvisor advisor = new AdaptiveLimitAdvisor(engine, limiter, integrationProperties, clock);

        completedWindowStore = new CompletedWindowStore();
        anomalyProvider = mock(AnomalyAssessmentProvider.class);
        DashboardHistoryProperties historyProperties = new DashboardHistoryProperties();
        historyService = new DashboardHistoryService(
                new DashboardHistoryStore(historyProperties), historyProperties, clock);

        scheduler = new AdaptiveEvaluationScheduler(completedWindowStore, anomalyProvider, advisor,
                new NoopAdaptiveEvaluationLock(), historyService);
    }

    @Test
    @DisplayName("scheduled evaluation applies an anomalous decision to the limiter")
    void scheduledEvaluationAppliesDecision() {
        completedWindowStore.publish(metricsWithSequence(1L));
        when(anomalyProvider.assess(any())).thenReturn(new AnomalyAssessment(true, 0.8, "anomaly", "IsolationForest"));

        scheduler.evaluate();

        assertThat(limiter.getCurrentLimit()).isEqualTo(90);
    }

    @Test
    @DisplayName("scheduled evaluation records anomaly, decision, and applied limit history")
    void scheduledEvaluationRecordsDashboardHistory() {
        completedWindowStore.publish(metricsWithSequence(1L));
        when(anomalyProvider.assess(any())).thenReturn(
                new AnomalyAssessment(true, 0.8, "anomaly", "IsolationForest"));

        scheduler.evaluate();

        var history = historyService.snapshot();
        assertThat(history.anomalies()).singleElement().satisfies(record -> {
            assertThat(record.windowSequence()).isEqualTo(1L);
            assertThat(record.isAnomaly()).isTrue();
            assertThat(record.anomalyScore()).isEqualTo(0.8);
        });
        assertThat(history.decisions()).singleElement().satisfies(record -> {
            assertThat(record.action()).isEqualTo("DECREASE_LIMIT");
            assertThat(record.previousLimit()).isEqualTo(100);
            assertThat(record.appliedLimit()).isEqualTo(90);
        });
        assertThat(history.limits()).singleElement().satisfies(record -> {
            assertThat(record.previousLimit()).isEqualTo(100);
            assertThat(record.limit()).isEqualTo(90);
        });
    }

    @Test
    @DisplayName("scheduled evaluation keeps the limit when the anomaly service is unavailable")
    void unavailableAnomalyKeepsLimit() {
        completedWindowStore.publish(metricsWithSequence(1L));
        when(anomalyProvider.assess(any())).thenReturn(AnomalyAssessment.unavailable());

        scheduler.evaluate();

        assertThat(limiter.getCurrentLimit()).isEqualTo(100);
    }

    @Test
    @DisplayName("scheduled evaluation forwards completed-window features to the ML provider")
    void scheduledEvaluationUsesCompletedWindowFeatures() {
        completedWindowStore.publish(new TrafficMetricsDto(
                1000L, 990L, 10L, 0.4, 60.0, 55.0, Instant.now(Clock.systemUTC()),
                25L, 20L, 5L, 0.08, 1L, Instant.parse("2026-11-04T00:00:00Z"),
                Instant.parse("2026-11-04T00:00:30Z"), 30.0));
        when(anomalyProvider.assess(any())).thenReturn(
                new AnomalyAssessment(false, -0.1, "normal", "IsolationForest"));

        scheduler.evaluate();

        ArgumentCaptor<TrafficAssessment> captor = ArgumentCaptor.forClass(TrafficAssessment.class);
        verify(anomalyProvider).assess(captor.capture());
        assertThat(captor.getValue().getEffectiveRequestCount()).isEqualTo(25L);
        assertThat(captor.getValue().getEffectiveErrorRate()).isEqualTo(0.08);
    }

    @Test
    @DisplayName("scheduled evaluation does not propagate unexpected provider failures")
    void providerFailureDoesNotPropagate() {
        completedWindowStore.publish(metricsWithSequence(1L));
        when(anomalyProvider.assess(any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(scheduler::evaluate).doesNotThrowAnyException();
        assertThat(limiter.getCurrentLimit()).isEqualTo(100);
    }

    @Test
    @DisplayName("the same completed window is never evaluated twice")
    void sameWindowIsEvaluatedOnlyOnce() {
        completedWindowStore.publish(metricsWithSequence(1L));
        when(anomalyProvider.assess(any())).thenReturn(normalAssessment());

        scheduler.evaluate();
        scheduler.evaluate();
        scheduler.evaluate();

        verify(anomalyProvider, times(1)).assess(any());
    }

    @Test
    @DisplayName("older window sequences are ignored")
    void olderWindowSequenceIsIgnored() {
        completedWindowStore.publish(metricsWithSequence(5L));
        completedWindowStore.publish(metricsWithSequence(4L));
        when(anomalyProvider.assess(any())).thenReturn(normalAssessment());

        scheduler.evaluate();
        scheduler.evaluate();

        verify(anomalyProvider, times(1)).assess(any());
    }

    @Test
    @DisplayName("newer window sequences are processed chronologically")
    void newerWindowSequenceIsProcessed() {
        completedWindowStore.publish(metricsWithSequence(1L));
        completedWindowStore.publish(metricsWithSequence(2L));
        when(anomalyProvider.assess(any())).thenReturn(normalAssessment());

        scheduler.evaluate();
        scheduler.evaluate();

        verify(anomalyProvider, times(2)).assess(any());
    }

    @Test
    @DisplayName("a provider failure retries the same window on the next run")
    void providerFailureRetriesTheSameWindow() {
        completedWindowStore.publish(metricsWithSequence(1L));
        when(anomalyProvider.assess(any()))
                .thenThrow(new IllegalStateException("ml unavailable"))
                .thenReturn(normalAssessment());

        assertThatCode(scheduler::evaluate).doesNotThrowAnyException();
        assertThat(completedWindowStore.size()).isEqualTo(1);

        scheduler.evaluate();

        assertThat(completedWindowStore.size()).isZero();
        verify(anomalyProvider, times(2)).assess(any());
    }

    @Test
    @DisplayName("windows are processed in ascending sequence order without replaying older ones")
    void windowsAreProcessedInAscendingOrder() {
        completedWindowStore.publish(metricsWithSequence(1L));
        completedWindowStore.publish(metricsWithSequence(3L));
        completedWindowStore.publish(metricsWithSequence(2L));
        when(anomalyProvider.assess(any())).thenReturn(normalAssessment());

        scheduler.evaluate();
        scheduler.evaluate();
        scheduler.evaluate();

        ArgumentCaptor<TrafficAssessment> captor = ArgumentCaptor.forClass(TrafficAssessment.class);
        verify(anomalyProvider, times(2)).assess(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(TrafficAssessment::getWindowSequence)
                .containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("overlapping evaluations are rejected by the scheduler guard")
    void overlappingEvaluationIsSkipped() throws Exception {
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        completedWindowStore.publish(metricsWithSequence(1L));
        when(anomalyProvider.assess(any())).thenAnswer(invocation -> {
            providerEntered.countDown();
            if (!releaseProvider.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test timed out waiting to release provider");
            }
            return normalAssessment();
        });

        Thread first = new Thread(scheduler::evaluate);
        first.start();
        assertThat(providerEntered.await(5, TimeUnit.SECONDS)).isTrue();

        // The first evaluation is still blocked inside the provider. A second
        // call must return immediately without invoking the provider again.
        scheduler.evaluate();
        verify(anomalyProvider, times(1)).assess(any());

        releaseProvider.countDown();
        first.join(5000);
        assertThat(first.isAlive()).isFalse();
    }

    @Test
    @DisplayName("a published window is evaluated within the same update call (no one-window delay)")
    void publishingWindowTriggersEvaluationWithoutDelay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-04T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringService monitoringService = monitoringServiceFor(metrics);
        when(anomalyProvider.assess(any())).thenReturn(normalAssessment());

        metrics.recordRequest();
        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));

        monitoringService.updateObservations();

        assertThat(completedWindowStore.size()).isZero();
        verify(anomalyProvider, times(1)).assess(any());
    }

    @Test
    @DisplayName("each published window is evaluated exactly once, in ascending order")
    void eachPublishedWindowIsEvaluatedOnceInOrder() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-04T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringService monitoringService = monitoringServiceFor(metrics);
        when(anomalyProvider.assess(any())).thenReturn(normalAssessment());

        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));
        monitoringService.updateObservations();

        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));
        monitoringService.updateObservations();

        ArgumentCaptor<TrafficAssessment> captor = ArgumentCaptor.forClass(TrafficAssessment.class);
        verify(anomalyProvider, times(2)).assess(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(TrafficAssessment::getWindowSequence)
                .containsExactly(1L, 2L);
        assertThat(completedWindowStore.size()).isZero();
    }

    @Test
    @DisplayName("a failed evaluation is retried by the next published window")
    void failedEvaluationIsRetriedOnNextPublication() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-04T00:00:00Z"));
        TrafficMetrics metrics = new TrafficMetrics(clock);
        MonitoringService monitoringService = monitoringServiceFor(metrics);
        when(anomalyProvider.assess(any()))
                .thenThrow(new IllegalStateException("ml unavailable"))
                .thenReturn(normalAssessment());

        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));
        monitoringService.updateObservations();
        assertThat(completedWindowStore.size()).isEqualTo(1);

        metrics.recordRequest();
        clock.advance(Duration.ofSeconds(10));
        monitoringService.updateObservations();

        assertThat(completedWindowStore.size()).isZero();
        verify(anomalyProvider, times(3)).assess(any());
    }

    @Test
    @DisplayName("concurrent publication triggers never evaluate the same window twice")
    void concurrentTriggersEvaluateWindowOnce() throws Exception {
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        completedWindowStore.publish(metricsWithSequence(1L));
        when(anomalyProvider.assess(any())).thenAnswer(invocation -> {
            providerEntered.countDown();
            if (!releaseProvider.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test timed out waiting to release provider");
            }
            return normalAssessment();
        });

        Thread first = new Thread(() -> scheduler.onWindowPublished(metricsWithSequence(1L)));
        first.start();
        assertThat(providerEntered.await(5, TimeUnit.SECONDS)).isTrue();

        // A concurrent trigger must be rejected while the first evaluation runs.
        scheduler.onWindowPublished(metricsWithSequence(1L));
        verify(anomalyProvider, times(1)).assess(any());

        releaseProvider.countDown();
        first.join(5000);
        assertThat(first.isAlive()).isFalse();
        assertThat(completedWindowStore.size()).isZero();
        verify(anomalyProvider, times(1)).assess(any());
    }

    private MonitoringService monitoringServiceFor(TrafficMetrics metrics) {
        MonitoringProperties monitoringProperties = new MonitoringProperties();
        monitoringProperties.setEwmaAlpha(0.5);
        monitoringProperties.setObservationInterval(Duration.ofSeconds(10));
        return new MonitoringService(metrics, monitoringProperties, completedWindowStore, List.of(scheduler));
    }

    private TrafficMetricsDto metricsWithSequence(long sequence) {
        return new TrafficMetricsDto(
                1000L, 990L, 10L, 0.01, 60.0, 55.0, Instant.parse("2026-11-04T00:00:00Z"),
                25L, 20L, 5L, 0.08, sequence,
                Instant.parse("2026-11-04T00:00:00Z"),
                Instant.parse("2026-11-04T00:00:30Z"), 30.0);
    }

    private AnomalyAssessment normalAssessment() {
        return new AnomalyAssessment(false, -0.1, "normal", "IsolationForest");
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        private void advance(Duration duration) {
            this.now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public long millis() {
            return now.toEpochMilli();
        }
    }
}
