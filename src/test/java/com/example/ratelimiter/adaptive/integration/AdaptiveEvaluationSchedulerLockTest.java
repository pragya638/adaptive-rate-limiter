package com.example.ratelimiter.adaptive.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.ratelimiter.adaptive.AdaptiveDecision;
import com.example.ratelimiter.adaptive.AdaptiveLimitAdvisor;
import com.example.ratelimiter.adaptive.AnomalyAssessment;
import com.example.ratelimiter.adaptive.AnomalyAssessmentProvider;
import com.example.ratelimiter.adaptive.DecisionAction;
import com.example.ratelimiter.adaptive.lock.AdaptiveEvaluationLock;
import com.example.ratelimiter.monitoring.CompletedWindowStore;
import com.example.ratelimiter.monitoring.TrafficMetricsDto;

class AdaptiveEvaluationSchedulerLockTest {

    private CompletedWindowStore completedWindowStore;
    private AnomalyAssessmentProvider anomalyProvider;
    private AdaptiveLimitAdvisor advisor;
    private AdaptiveEvaluationLock lock;
    private AdaptiveEvaluationScheduler scheduler;

    @BeforeEach
    void setUp() {
        completedWindowStore = new CompletedWindowStore();
        anomalyProvider = mock(AnomalyAssessmentProvider.class);
        advisor = mock(AdaptiveLimitAdvisor.class);
        lock = mock(AdaptiveEvaluationLock.class);
        scheduler = new AdaptiveEvaluationScheduler(completedWindowStore, anomalyProvider, advisor, lock);

        completedWindowStore.publish(new TrafficMetricsDto(
                1000L, 990L, 10L, 0.01, 60.0, 55.0, Instant.now(),
                25L, 20L, 5L, 0.08, 1L,
                Instant.parse("2026-11-04T00:00:00Z"),
                Instant.parse("2026-11-04T00:00:30Z"), 30.0));
        when(anomalyProvider.assess(any())).thenReturn(AnomalyAssessment.unavailable());
        when(advisor.evaluateAndApply(any(), any())).thenReturn(new AdaptiveDecision(
                DecisionAction.KEEP_LIMIT, 10, 10, "KEEP", "test", Instant.now()));
    }

    @Test
    void skipsEvaluationWhenAnotherInstanceHoldsTheLock() {
        when(lock.tryAcquire()).thenReturn(false);

        scheduler.evaluate();

        verifyNoInteractions(anomalyProvider, advisor);
        verify(lock, never()).release();
    }

    @Test
    void skippedWindowCanBeEvaluatedAfterBecomingLeader() {
        when(lock.tryAcquire()).thenReturn(false, true);

        scheduler.evaluate();
        scheduler.evaluate();

        verify(anomalyProvider, times(1)).assess(any());
        verify(lock, times(1)).release();
    }

    @Test
    void releasesLockAfterSuccessfulEvaluation() {
        when(lock.tryAcquire()).thenReturn(true);

        scheduler.evaluate();

        verify(advisor).evaluateAndApply(any(), any());
        verify(lock).release();
    }

    @Test
    void releasesLockWhenEvaluationFails() {
        when(lock.tryAcquire()).thenReturn(true);
        when(anomalyProvider.assess(any())).thenThrow(new IllegalStateException("boom"));

        scheduler.evaluate();

        verify(lock).release();
    }
}
