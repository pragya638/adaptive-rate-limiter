package com.example.ratelimiter.adaptive;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.ratelimiter.ratelimit.DynamicRateLimiter;

class AdaptiveLimitAdvisorTest {

    private static final Duration WINDOW = Duration.ofSeconds(30);

    private AdaptiveEngineProperties engineProperties;
    private AdaptiveIntegrationProperties integrationProperties;
    private TestClock clock;
    private DynamicRateLimiter limiter;
    private AdaptiveDecisionEngine engine;
    private AdaptiveLimitAdvisor advisor;

    @BeforeEach
    void setUp() {
        engineProperties = new AdaptiveEngineProperties();
        engineProperties.setMinLimit(10);
        engineProperties.setMaxLimit(1000);
        engineProperties.setInitialLimit(100);
        engineProperties.setIncreaseStep(10);
        engineProperties.setDecreaseStep(20);
        engineProperties.setHighErrorRateThreshold(0.1);
        engineProperties.setSpikeRatioThreshold(1.5);
        engineProperties.setGrowthRatioThreshold(1.2);
        engineProperties.setAdjustmentThreshold(0.05);

        integrationProperties = new AdaptiveIntegrationProperties();
        integrationProperties.setCooldownSeconds(0);

        clock = new TestClock(Instant.parse("2026-11-04T00:00:00Z"));
        limiter = new DynamicRateLimiter(100, 10, 1000, WINDOW, clock);
        engine = new AdaptiveDecisionEngine(engineProperties, clock);
        advisor = new AdaptiveLimitAdvisor(engine, limiter, integrationProperties, clock);
    }

    @Test
    @DisplayName("an anomalous assessment decreases the active limit")
    void anomalyDecreasesLimit() {
        AdaptiveDecision decision = advisor.evaluateAndApply(normalTraffic(), anomalous());

        assertThat(decision.getAction()).isEqualTo(DecisionAction.DECREASE_LIMIT);
        assertThat(decision.getProposedLimit()).isEqualTo(80);
        assertThat(limiter.getCurrentLimit()).isEqualTo(80);
    }

    @Test
    @DisplayName("sustained traffic growth increases the active limit")
    void growthIncreasesLimit() {
        TrafficAssessment traffic = new TrafficAssessment(100.0, 50.0, 0.01, 2000L, 1980L, 20L);

        AdaptiveDecision decision = advisor.evaluateAndApply(traffic, normalAnomaly());

        assertThat(decision.getAction()).isEqualTo(DecisionAction.INCREASE_LIMIT);
        assertThat(decision.getProposedLimit()).isEqualTo(110);
        assertThat(limiter.getCurrentLimit()).isEqualTo(110);
    }

    @Test
    @DisplayName("an unavailable anomaly service never increases the limit")
    void unavailableAnomalyUsesConservativeFallback() {
        TrafficAssessment traffic = new TrafficAssessment(2000.0, 100.0, 0.01, 10000L, 9900L, 100L);

        AdaptiveDecision decision = advisor.evaluateAndApply(traffic, AnomalyAssessment.unavailable());

        assertThat(decision.getAction()).isEqualTo(DecisionAction.KEEP_LIMIT);
        assertThat(limiter.getCurrentLimit()).isEqualTo(100);
    }

    @Test
    @DisplayName("the limiter never moves below its configured minimum")
    void limitRespectsMinimumBound() {
        limiter = new DynamicRateLimiter(15, 10, 1000, WINDOW, clock);
        engineProperties.setInitialLimit(15);
        engine = new AdaptiveDecisionEngine(engineProperties, clock);
        advisor = new AdaptiveLimitAdvisor(engine, limiter, integrationProperties, clock);

        advisor.evaluateAndApply(normalTraffic(), anomalous());
        assertThat(limiter.getCurrentLimit()).isEqualTo(10);

        advisor.evaluateAndApply(normalTraffic(), anomalous());
        assertThat(limiter.getCurrentLimit()).isEqualTo(10);
    }

    @Test
    @DisplayName("updates are skipped while the cooldown is active")
    void cooldownSkipsUpdates() {
        integrationProperties.setCooldownSeconds(60);
        advisor = new AdaptiveLimitAdvisor(engine, limiter, integrationProperties, clock);

        AdaptiveDecision first = advisor.evaluateAndApply(normalTraffic(), anomalous());
        assertThat(first.getAction()).isEqualTo(DecisionAction.DECREASE_LIMIT);
        assertThat(limiter.getCurrentLimit()).isEqualTo(80);

        AdaptiveDecision second = advisor.evaluateAndApply(normalTraffic(), anomalous());
        assertThat(second.getAction()).isEqualTo(DecisionAction.KEEP_LIMIT);
        assertThat(second.getReasonCode()).isEqualTo("COOLDOWN_ACTIVE");
        assertThat(limiter.getCurrentLimit()).isEqualTo(80);

        clock.advanceBy(Duration.ofSeconds(61));
        AdaptiveDecision third = advisor.evaluateAndApply(normalTraffic(), anomalous());
        assertThat(third.getAction()).isEqualTo(DecisionAction.DECREASE_LIMIT);
        assertThat(limiter.getCurrentLimit()).isEqualTo(60);
    }

    private TrafficAssessment normalTraffic() {
        return new TrafficAssessment(60.0, 55.0, 0.01, 1000L, 990L, 10L);
    }

    private AnomalyAssessment anomalous() {
        return new AnomalyAssessment(true, 0.5, "anomaly", "IsolationForest");
    }

    private AnomalyAssessment normalAnomaly() {
        return new AnomalyAssessment(false, -0.1, "normal", "IsolationForest");
    }

    private static final class TestClock extends Clock {

        private Instant instant;

        private TestClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceBy(Duration duration) {
            instant = instant.plus(duration);
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
            return instant;
        }

        @Override
        public long millis() {
            return instant.toEpochMilli();
        }
    }
}
