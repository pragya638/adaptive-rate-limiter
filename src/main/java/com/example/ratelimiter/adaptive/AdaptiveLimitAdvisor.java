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

    private Instant lastEvaluationAt;

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
        if (isCooldownActive(now)) {
            int currentLimit = rateLimiter.getCurrentLimit();
            return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, currentLimit, currentLimit,
                    "COOLDOWN_ACTIVE", "Adaptive update skipped during cooldown.", now);
        }

        AdaptiveDecision decision = decisionEngine.evaluate(traffic, anomaly);
        rateLimiter.updateLimit(decision.getProposedLimit());
        lastEvaluationAt = now;
        return decision;
    }

    public int getCurrentLimit() {
        return rateLimiter.getCurrentLimit();
    }

    private boolean isCooldownActive(Instant now) {
        if (lastEvaluationAt == null) {
            return false;
        }
        long cooldownSeconds = properties.getCooldownSeconds();
        return Duration.between(lastEvaluationAt, now).getSeconds() < cooldownSeconds;
    }
}
