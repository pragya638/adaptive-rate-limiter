package com.example.ratelimiter.adaptive;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.ratelimiter.ratelimit.DynamicRateLimiter;

/**
 * Safely applies {@link AdaptiveDecisionEngine} decisions to a
 * {@link DynamicRateLimiter}.
 *
 * <p>The advisor is the only component that mutates the dynamic limit. It
 * evaluates decisions on the scheduler thread, applies them at most once per
 * configured cooldown window, and relies on the limiter to clamp the final
 * value to the configured minimum and maximum bounds.
 */
@Component
@ConditionalOnProperty(name = "adaptive.enabled", havingValue = "true")
public class AdaptiveLimitAdvisor {

    private final AdaptiveDecisionEngine decisionEngine;
    private final DynamicRateLimiter rateLimiter;
    private final AdaptiveIntegrationProperties properties;
    private final Clock clock;

    private volatile Instant lastEvaluationAt;
    private volatile Instant lastAdaptationAt;
    private volatile AdaptiveDecision lastDecision;
    private volatile AnomalyAssessment lastAnomalyAssessment;
    private volatile TrafficAssessment lastTrafficAssessment;

    public AdaptiveLimitAdvisor(AdaptiveDecisionEngine decisionEngine, DynamicRateLimiter rateLimiter,
            AdaptiveIntegrationProperties properties, Clock clock) {
        this.decisionEngine = decisionEngine;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Evaluates traffic and applies the resulting limit when the cooldown has
     * elapsed. This method is synchronized so overlapping scheduler executions
     * cannot interleave limit updates.
     */
    public synchronized AdaptiveDecision evaluateAndApply(TrafficAssessment traffic, AnomalyAssessment anomaly) {
        Instant now = clock.instant();
        lastAnomalyAssessment = anomaly;
        lastTrafficAssessment = traffic;

        if (isCooldownActive(now)) {
            int currentLimit = rateLimiter.getCurrentLimit();
            AdaptiveDecision skipped = new AdaptiveDecision(DecisionAction.KEEP_LIMIT, currentLimit, currentLimit,
                    "COOLDOWN_ACTIVE", "Adaptive update skipped during cooldown.", now);
            lastDecision = skipped;
            return skipped;
        }

        // Only record the evaluation time when the engine actually runs. If
        // every skipped call refreshed this timestamp, frequent callers could
        // extend the cooldown forever and starve adaptation.
        lastEvaluationAt = now;
        AdaptiveDecision decision = decisionEngine.evaluate(traffic, anomaly);
        int previousLimit = rateLimiter.getCurrentLimit();
        int appliedLimit = rateLimiter.updateLimit(decision.getProposedLimit());
        if (appliedLimit != previousLimit) {
            lastAdaptationAt = now;
        }
        lastDecision = decision;
        return decision;
    }

    public int getCurrentLimit() {
        return rateLimiter.getCurrentLimit();
    }

    public Instant getLastEvaluationAt() {
        return lastEvaluationAt;
    }

    public Instant getLastAdaptationAt() {
        return lastAdaptationAt;
    }

    public AdaptiveDecision getLastDecision() {
        return lastDecision;
    }

    public AnomalyAssessment getLastAnomalyAssessment() {
        return lastAnomalyAssessment;
    }

    public TrafficAssessment getLastTrafficAssessment() {
        return lastTrafficAssessment;
    }

    private boolean isCooldownActive(Instant now) {
        if (lastEvaluationAt == null) {
            return false;
        }
        long cooldownSeconds = properties.getCooldownSeconds();
        return Duration.between(lastEvaluationAt, now).getSeconds() < cooldownSeconds;
    }
}
