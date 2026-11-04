package com.example.ratelimiter.adaptive.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.ratelimiter.adaptive.AdaptiveDecision;
import com.example.ratelimiter.adaptive.AdaptiveLimitAdvisor;
import com.example.ratelimiter.adaptive.AnomalyAssessment;
import com.example.ratelimiter.adaptive.AnomalyAssessmentProvider;
import com.example.ratelimiter.adaptive.TrafficAssessment;
import com.example.ratelimiter.monitoring.MonitoringService;
import com.example.ratelimiter.monitoring.TrafficMetricsDto;

/**
 * Periodically feeds monitoring metrics and ML anomaly information into the
 * adaptive decision engine through {@link AdaptiveLimitAdvisor}.
 *
 * <p>The scheduler only exists when {@code adaptive.enabled=true}. It runs in
 * the background, catches all runtime failures, and never participates in the
 * request path.
 */
@Component
@ConditionalOnProperty(name = "adaptive.enabled", havingValue = "true")
public class AdaptiveEvaluationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveEvaluationScheduler.class);

    private final MonitoringService monitoringService;
    private final AnomalyAssessmentProvider anomalyAssessmentProvider;
    private final AdaptiveLimitAdvisor advisor;

    public AdaptiveEvaluationScheduler(MonitoringService monitoringService,
            AnomalyAssessmentProvider anomalyAssessmentProvider, AdaptiveLimitAdvisor advisor) {
        this.monitoringService = monitoringService;
        this.anomalyAssessmentProvider = anomalyAssessmentProvider;
        this.advisor = advisor;
    }

    @Scheduled(initialDelayString = "${adaptive.evaluation-interval-seconds:30}000",
            fixedDelayString = "${adaptive.evaluation-interval-seconds:30}000")
    public void evaluate() {
        try {
            TrafficMetricsDto metrics = monitoringService.getMetrics();
            TrafficAssessment traffic = new TrafficAssessment(
                    metrics.getCurrentRequestRate(),
                    metrics.getEwmaRequestRate(),
                    metrics.getErrorRate(),
                    metrics.getTotalRequests(),
                    metrics.getAllowedRequests(),
                    metrics.getRejectedRequests());

            AnomalyAssessment anomaly = anomalyAssessmentProvider.assess(traffic);
            AdaptiveDecision decision = advisor.evaluateAndApply(traffic, anomaly);

            log.debug("Adaptive evaluation action={} currentLimit={} proposedLimit={} reason={}",
                    decision.getAction(), decision.getCurrentLimit(), decision.getProposedLimit(),
                    decision.getReasonCode());
        } catch (RuntimeException ex) {
            log.warn("Adaptive evaluation failed; keeping the current limit", ex);
        }
    }
}
