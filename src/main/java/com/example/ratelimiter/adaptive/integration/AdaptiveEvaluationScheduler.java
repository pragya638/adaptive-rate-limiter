package com.example.ratelimiter.adaptive.integration;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.ratelimiter.adaptive.AdaptiveDecision;
import com.example.ratelimiter.adaptive.AdaptiveLimitAdvisor;
import com.example.ratelimiter.adaptive.AnomalyAssessment;
import com.example.ratelimiter.adaptive.AnomalyAssessmentProvider;
import com.example.ratelimiter.adaptive.TrafficAssessment;
import com.example.ratelimiter.adaptive.lock.AdaptiveEvaluationLock;
import com.example.ratelimiter.adaptive.lock.NoopAdaptiveEvaluationLock;
import com.example.ratelimiter.history.DashboardHistoryService;
import com.example.ratelimiter.monitoring.CompletedWindowListener;
import com.example.ratelimiter.monitoring.CompletedWindowStore;
import com.example.ratelimiter.monitoring.TrafficMetricsDto;

/**
 * Feeds completed monitoring windows and ML anomaly information into the
 * adaptive decision engine through {@link AdaptiveLimitAdvisor}.
 *
 * <p>The evaluator only exists when {@code adaptive.enabled=true}. It never
 * participates in the request path and catches all runtime failures.
 *
 * <p>{@link com.example.ratelimiter.monitoring.MonitoringService} publishes
 * each window only after its counters, rate, EWMA, and sequence have been
 * captured together, then notifies this component through
 * {@link #onWindowPublished(TrafficMetricsDto)}. Evaluation is therefore driven
 * deterministically by publication instead of by an independent timer, which
 * removes the previous one-window delay and keeps monitoring and evaluation in
 * a single, consistent order.
 *
 * <p>This component consumes the published snapshots in ascending sequence
 * order and acknowledges (removes) a window only after a successful evaluation,
 * so a failed ML call is retried on the next published window. A single-flight
 * guard prevents overlapping evaluations, and the leader lock keeps multi-instance
 * deployments from evaluating the same window twice.
 */
@Component
@ConditionalOnProperty(name = "adaptive.enabled", havingValue = "true")
public class AdaptiveEvaluationScheduler implements CompletedWindowListener {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveEvaluationScheduler.class);

    private final CompletedWindowStore completedWindowStore;
    private final AnomalyAssessmentProvider anomalyAssessmentProvider;
    private final AdaptiveLimitAdvisor advisor;
    private final AdaptiveEvaluationLock evaluationLock;
    private final DashboardHistoryService historyService;

    private final AtomicBoolean evaluating = new AtomicBoolean(false);
    private volatile long lastProcessedWindowSequence = Long.MIN_VALUE;

    public AdaptiveEvaluationScheduler(CompletedWindowStore completedWindowStore,
            AnomalyAssessmentProvider anomalyAssessmentProvider, AdaptiveLimitAdvisor advisor) {
        this(completedWindowStore, anomalyAssessmentProvider, advisor, new NoopAdaptiveEvaluationLock(), null);
    }

    public AdaptiveEvaluationScheduler(CompletedWindowStore completedWindowStore,
            AnomalyAssessmentProvider anomalyAssessmentProvider, AdaptiveLimitAdvisor advisor,
            AdaptiveEvaluationLock evaluationLock) {
        this(completedWindowStore, anomalyAssessmentProvider, advisor, evaluationLock, null);
    }

    @Autowired
    public AdaptiveEvaluationScheduler(CompletedWindowStore completedWindowStore,
            AnomalyAssessmentProvider anomalyAssessmentProvider, AdaptiveLimitAdvisor advisor,
            AdaptiveEvaluationLock evaluationLock, DashboardHistoryService historyService) {
        this.completedWindowStore = completedWindowStore;
        this.anomalyAssessmentProvider = anomalyAssessmentProvider;
        this.advisor = advisor;
        this.evaluationLock = evaluationLock;
        this.historyService = historyService;
    }

    /**
     * Deterministic handoff entry point. Invoked by
     * {@link com.example.ratelimiter.monitoring.MonitoringService} right after a
     * completed window has been published, so evaluation always follows
     * publication instead of racing an independent schedule.
     */
    @Override
    public void onWindowPublished(TrafficMetricsDto window) {
        evaluate();
    }

    public void evaluate() {
        if (!evaluating.compareAndSet(false, true)) {
            log.debug("Skipping adaptive evaluation because a previous evaluation is still running");
            return;
        }
        try {
            evaluatePendingWindows();
        } catch (RuntimeException ex) {
            log.warn("Adaptive evaluation failed; keeping the current limit", ex);
        } finally {
            evaluating.set(false);
        }
    }

    private void evaluatePendingWindows() {
        int evaluated = 0;
        while (true) {
            TrafficMetricsDto metrics = completedWindowStore.peekFirst();
            if (metrics == null) {
                break;
            }

            long sequence = metrics.getWindowSequence();
            if (sequence <= lastProcessedWindowSequence) {
                completedWindowStore.removeFirst();
                log.debug("Skipping window sequence {} because it was already evaluated (lastProcessed={})",
                        sequence, lastProcessedWindowSequence);
                continue;
            }

            if (!evaluationLock.tryAcquire()) {
                log.debug("Skipping adaptive evaluation because another instance holds the leader lock");
                break;
            }

            try {
                evaluateWindow(metrics);
                completedWindowStore.removeFirst();
                lastProcessedWindowSequence = sequence;
                evaluated++;
            } catch (RuntimeException ex) {
                log.warn("Adaptive evaluation failed for window {}; keeping it for retry", sequence, ex);
                break;
            } finally {
                evaluationLock.release();
            }
        }

        if (evaluated > 0) {
            log.debug("Adaptive evaluation processed {} completed window(s)", evaluated);
        }
    }

    private void evaluateWindow(TrafficMetricsDto metrics) {
        TrafficAssessment traffic = new TrafficAssessment(
                metrics.getCurrentRequestRate(),
                metrics.getEwmaRequestRate(),
                metrics.getErrorRate(),
                metrics.getTotalRequests(),
                metrics.getAllowedRequests(),
                metrics.getRejectedRequests(),
                metrics.getWindowRequestCount(),
                metrics.getClientErrorRate(),
                metrics.getWindowSequence(),
                metrics.getWindowDurationSeconds());

        AnomalyAssessment anomaly = anomalyAssessmentProvider.assess(traffic);
        AdaptiveDecision decision = advisor.evaluateAndApply(traffic, anomaly);

        log.debug("Adaptive evaluation window={} action={} currentLimit={} proposedLimit={} reason={}",
                metrics.getWindowSequence(), decision.getAction(), decision.getCurrentLimit(),
                decision.getProposedLimit(), decision.getReasonCode());

        if (historyService != null) {
            try {
                // Record after the decision has been applied, using the actual
                // limiter value so clamped decisions are represented honestly.
                historyService.recordEvaluation(metrics, anomaly, decision, advisor.getCurrentLimit());
            } catch (RuntimeException ex) {
                // History is diagnostic only. Never retry an applied decision
                // merely because recording failed.
                log.warn("Unable to record adaptive history for window {}",
                        metrics.getWindowSequence(), ex);
            }
        }
    }
}
