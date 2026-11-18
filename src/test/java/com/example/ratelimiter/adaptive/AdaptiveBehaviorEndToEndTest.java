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

/**
 * Deterministic end-to-end behaviour of the adaptive loop without the HTTP or
 * ML layers: metrics -> decision engine -> advisor -> dynamic limiter.
 */
class AdaptiveBehaviorEndToEndTest {

    private static final Duration WINDOW = Duration.ofSeconds(30);
    private static final String CLIENT = "client-a";

    private TestClock clock;
    private DynamicRateLimiter limiter;
    private AdaptiveDecisionEngine engine;
    private AdaptiveLimitAdvisor advisor;

    @BeforeEach
    void setUp() {
        AdaptiveEngineProperties properties = new AdaptiveEngineProperties();
        properties.setMinLimit(1);
        properties.setMaxLimit(50);
        properties.setInitialLimit(5);
        properties.setIncreaseStep(5);
        properties.setDecreaseStep(10);
        properties.setDecreaseFraction(0.2);
        properties.setRecoveryStep(1);
        properties.setRecoveryWindows(2);
        properties.setHighErrorRateThreshold(0.1);
        properties.setAnomalyConfirmationWindows(1);
        properties.setGrowthConfirmationWindows(1);

        AdaptiveIntegrationProperties integration = new AdaptiveIntegrationProperties();
        integration.setCooldownSeconds(0);

        clock = new TestClock(Instant.parse("2026-11-04T00:00:00Z"));
        limiter = new DynamicRateLimiter(5, 1, 50, WINDOW, clock);
        engine = new AdaptiveDecisionEngine(properties, clock);
        advisor = new AdaptiveLimitAdvisor(engine, limiter, integration, clock);
    }

    @Test
    @DisplayName("normal traffic keeps the limit and the limiter enforces it")
    void normalTrafficKeepsLimit() {
        AdaptiveDecision decision = advisor.evaluateAndApply(normalTraffic(), normal());

        assertThat(decision.getAction()).isEqualTo(DecisionAction.KEEP_LIMIT);
        assertThat(limiter.getCurrentLimit()).isEqualTo(5);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(CLIENT).allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire(CLIENT).allowed()).isFalse();
    }

    @Test
    @DisplayName("a burst reduces the limit gradually and the limiter applies it")
    void burstTrafficReducesLimit() {
        AdaptiveDecision decision = advisor.evaluateAndApply(burstTraffic(), normal());

        assertThat(decision.getAction()).isEqualTo(DecisionAction.DECREASE_LIMIT);
        assertThat(decision.getReasonCode()).isEqualTo("TRAFFIC_SPIKE");
        assertThat(limiter.getCurrentLimit()).isEqualTo(4);

        for (int i = 0; i < 4; i++) {
            assertThat(limiter.tryAcquire(CLIENT).allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire(CLIENT).allowed()).isFalse();
    }

    @Test
    @DisplayName("an isolated anomaly reduces a small limit by one, not to the minimum")
    void isolatedAnomalyReducesGradually() {
        AdaptiveDecision decision = advisor.evaluateAndApply(normalTraffic(), anomalous());

        assertThat(decision.getAction()).isEqualTo(DecisionAction.DECREASE_LIMIT);
        assertThat(decision.getProposedLimit()).isEqualTo(4);
        assertThat(limiter.getCurrentLimit()).isEqualTo(4);
    }

    @Test
    @DisplayName("sustained anomalies walk the limit down to the minimum and stop")
    void sustainedAnomaliesReachMinimum() {
        assertThat(advisor.evaluateAndApply(normalTraffic(), anomalous()).getProposedLimit()).isEqualTo(4);
        assertThat(advisor.evaluateAndApply(normalTraffic(), anomalous()).getProposedLimit()).isEqualTo(3);
        assertThat(advisor.evaluateAndApply(normalTraffic(), anomalous()).getProposedLimit()).isEqualTo(2);
        assertThat(advisor.evaluateAndApply(normalTraffic(), anomalous()).getProposedLimit()).isEqualTo(1);

        AdaptiveDecision atMinimum = advisor.evaluateAndApply(normalTraffic(), anomalous());
        assertThat(atMinimum.getAction()).isEqualTo(DecisionAction.KEEP_LIMIT);
        assertThat(atMinimum.getReasonCode()).isEqualTo("AT_MIN_LIMIT");
        assertThat(limiter.getCurrentLimit()).isEqualTo(1);

        assertThat(limiter.tryAcquire(CLIENT).allowed()).isTrue();
        assertThat(limiter.tryAcquire(CLIENT).allowed()).isFalse();
    }

    @Test
    @DisplayName("normal windows recover the limit gradually after anomalies")
    void normalWindowsRecoverLimit() {
        advisor.evaluateAndApply(normalTraffic(), anomalous());
        advisor.evaluateAndApply(normalTraffic(), anomalous());
        assertThat(limiter.getCurrentLimit()).isEqualTo(3);

        assertThat(advisor.evaluateAndApply(normalTraffic(), normal()).getProposedLimit()).isEqualTo(3);
        assertThat(advisor.evaluateAndApply(normalTraffic(), normal()).getProposedLimit()).isEqualTo(4);
        assertThat(advisor.evaluateAndApply(normalTraffic(), normal()).getProposedLimit()).isEqualTo(5);

        AdaptiveDecision atInitial = advisor.evaluateAndApply(normalTraffic(), normal());
        assertThat(atInitial.getAction()).isEqualTo(DecisionAction.KEEP_LIMIT);
        assertThat(limiter.getCurrentLimit()).isEqualTo(5);
    }

    private TrafficAssessment normalTraffic() {
        return new TrafficAssessment(50.0, 50.0, 0.0, 1000L, 1000L, 0L, 100L, 0.0);
    }

    private TrafficAssessment burstTraffic() {
        return new TrafficAssessment(150.0, 50.0, 0.0, 1500L, 1500L, 0L, 100L, 0.0);
    }

    private AnomalyAssessment normal() {
        return new AnomalyAssessment(false, -0.1, "normal", "IsolationForest");
    }

    private AnomalyAssessment anomalous() {
        return new AnomalyAssessment(true, 0.5, "anomaly", "IsolationForest");
    }

    private static final class TestClock extends Clock {

        private Instant instant;

        private TestClock(Instant instant) {
            this.instant = instant;
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
