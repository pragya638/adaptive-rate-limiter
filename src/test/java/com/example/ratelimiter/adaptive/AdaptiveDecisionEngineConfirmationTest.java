package com.example.ratelimiter.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused coverage for the confirmation, score-gate, corroboration, growth
 * bounding and overflow-safety corrections to the adaptive decision engine.
 */
class AdaptiveDecisionEngineConfirmationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private static AdaptiveEngineProperties baseProperties() {
        AdaptiveEngineProperties properties = new AdaptiveEngineProperties();
        properties.setMinLimit(1);
        properties.setMaxLimit(1000);
        properties.setInitialLimit(100);
        properties.setIncreaseStep(10);
        properties.setDecreaseStep(20);
        properties.setHighErrorRateThreshold(0.1);
        properties.setSpikeRatioThreshold(1.5);
        properties.setGrowthRatioThreshold(1.2);
        return properties;
    }

    private static AdaptiveDecisionEngine engine(AdaptiveEngineProperties properties) {
        return new AdaptiveDecisionEngine(properties, CLOCK);
    }

    private static TrafficAssessment calmTraffic() {
        return new TrafficAssessment(60.0, 55.0, 0.01, 1000L, 990L, 10L);
    }

    private static TrafficAssessment elevatedTraffic() {
        return new TrafficAssessment(65.0, 50.0, 0.01, 2000L, 1980L, 20L);
    }

    private static TrafficAssessment spikeTraffic() {
        return new TrafficAssessment(150.0, 50.0, 0.01, 1500L, 1485L, 15L);
    }

    private static AnomalyAssessment anomaly() {
        return new AnomalyAssessment(true, 0.5, "anomaly", "IsolationForest");
    }

    private static AnomalyAssessment normal() {
        return new AnomalyAssessment(false, -0.1, "normal", "IsolationForest");
    }

    @Test
    @DisplayName("defaults use two-window confirmation and disabled optional gates")
    void defaultsAreConservative() {
        AdaptiveEngineProperties properties = new AdaptiveEngineProperties();
        assertEquals(2, properties.getAnomalyConfirmationWindows());
        assertEquals(2, properties.getGrowthConfirmationWindows());
        assertNull(properties.getAnomalyScoreThreshold());
        assertNull(properties.getGrowthCeiling());
    }

    @Test
    @DisplayName("an isolated anomaly does not decrease the limit under the default confirmation")
    void isolatedAnomalyDoesNotDecrease() {
        AdaptiveDecisionEngine engine = engine(baseProperties());

        AdaptiveDecision decision = engine.evaluate(calmTraffic(), anomaly());

        assertEquals(DecisionAction.KEEP_LIMIT, decision.getAction());
        assertEquals("ANOMALY_PENDING", decision.getReasonCode());
        assertEquals(100, decision.getProposedLimit());
    }

    @Test
    @DisplayName("consecutive anomalies decrease the limit once confirmed")
    void consecutiveAnomaliesDecreaseAfterConfirmation() {
        AdaptiveDecisionEngine engine = engine(baseProperties());

        assertEquals("ANOMALY_PENDING", engine.evaluate(calmTraffic(), anomaly()).getReasonCode());

        AdaptiveDecision confirmed = engine.evaluate(calmTraffic(), anomaly());
        assertEquals(DecisionAction.DECREASE_LIMIT, confirmed.getAction());
        assertEquals("ANOMALY_DETECTED", confirmed.getReasonCode());
        assertEquals(80, confirmed.getProposedLimit());

        // Once confirmed, further consecutive anomalies keep decreasing.
        assertEquals(64, engine.evaluate(calmTraffic(), anomaly()).getProposedLimit());
    }

    @Test
    @DisplayName("an isolated pending anomaly does not reset the recovery counter")
    void pendingAnomalyDoesNotResetRecovery() {
        AdaptiveEngineProperties properties = baseProperties();
        properties.setRecoveryWindows(2);
        properties.setRecoveryStep(1);
        properties.setDecreaseStep(1);
        properties.setDecreaseFraction(0.01);
        AdaptiveDecisionEngine engine = engine(properties);

        engine.evaluate(calmTraffic(), anomaly());
        assertEquals(99, engine.evaluate(calmTraffic(), anomaly()).getProposedLimit());

        assertEquals("NORMAL_TRAFFIC", engine.evaluate(calmTraffic(), normal()).getReasonCode());
        assertEquals("ANOMALY_PENDING", engine.evaluate(calmTraffic(), anomaly()).getReasonCode());

        AdaptiveDecision recovery = engine.evaluate(calmTraffic(), normal());
        assertEquals(DecisionAction.INCREASE_LIMIT, recovery.getAction());
        assertEquals("RECOVERY", recovery.getReasonCode());
        assertEquals(100, recovery.getProposedLimit());
    }

    @Test
    @DisplayName("an anomaly below the configured score threshold is ignored")
    void anomalyBelowScoreThresholdIsIgnored() {
        AdaptiveEngineProperties properties = baseProperties();
        properties.setAnomalyConfirmationWindows(1);
        properties.setAnomalyScoreThreshold(0.5);
        AdaptiveDecisionEngine engine = engine(properties);

        AnomalyAssessment lowScore = new AnomalyAssessment(true, 0.3, "anomaly", "IsolationForest");
        assertEquals(DecisionAction.KEEP_LIMIT, engine.evaluate(calmTraffic(), lowScore).getAction());

        AnomalyAssessment highScore = new AnomalyAssessment(true, 0.8, "anomaly", "IsolationForest");
        assertEquals(DecisionAction.DECREASE_LIMIT, engine.evaluate(calmTraffic(), highScore).getAction());
    }

    @Test
    @DisplayName("a non-finite score falls back to the boolean anomaly result")
    void nonFiniteScoreFallsBackToBoolean() {
        AdaptiveEngineProperties properties = baseProperties();
        properties.setAnomalyConfirmationWindows(1);
        properties.setAnomalyScoreThreshold(0.5);
        AdaptiveDecisionEngine engine = engine(properties);

        AnomalyAssessment nonFinite = new AnomalyAssessment(true, Double.NaN, "anomaly", "IsolationForest");

        assertEquals(DecisionAction.DECREASE_LIMIT, engine.evaluate(calmTraffic(), nonFinite).getAction());
    }

    @Test
    @DisplayName("when corroboration is required, a calm anomaly is not treated as an attack")
    void anomalyRequiresCorroborationWhenEnabled() {
        AdaptiveEngineProperties properties = baseProperties();
        properties.setAnomalyConfirmationWindows(1);
        properties.setAnomalyRequireCorroboration(true);
        AdaptiveDecisionEngine engine = engine(properties);

        assertEquals(DecisionAction.KEEP_LIMIT, engine.evaluate(calmTraffic(), anomaly()).getAction());
        assertEquals(DecisionAction.DECREASE_LIMIT, engine.evaluate(elevatedTraffic(), anomaly()).getAction());
    }

    @Test
    @DisplayName("sustained growth requires consecutive growth windows")
    void growthRequiresConsecutiveWindows() {
        AdaptiveEngineProperties properties = baseProperties();
        properties.setGrowthConfirmationWindows(2);
        AdaptiveDecisionEngine engine = engine(properties);

        assertEquals("GROWTH_PENDING", engine.evaluate(elevatedTraffic(), normal()).getReasonCode());

        AdaptiveDecision grown = engine.evaluate(elevatedTraffic(), normal());
        assertEquals(DecisionAction.INCREASE_LIMIT, grown.getAction());
        assertEquals("SUSTAINED_GROWTH", grown.getReasonCode());
        assertEquals(110, grown.getProposedLimit());
    }

    @Test
    @DisplayName("the growth counter resets when growth is no longer observed")
    void growthCounterResetsWhenConditionStops() {
        AdaptiveEngineProperties properties = baseProperties();
        properties.setGrowthConfirmationWindows(2);
        AdaptiveDecisionEngine engine = engine(properties);

        assertEquals("GROWTH_PENDING", engine.evaluate(elevatedTraffic(), normal()).getReasonCode());
        assertEquals("NORMAL_TRAFFIC", engine.evaluate(calmTraffic(), normal()).getReasonCode());
        assertEquals("GROWTH_PENDING", engine.evaluate(elevatedTraffic(), normal()).getReasonCode());
        assertEquals(DecisionAction.INCREASE_LIMIT, engine.evaluate(elevatedTraffic(), normal()).getAction());
    }

    @Test
    @DisplayName("growth stops at the configured ceiling and never exceeds max-limit")
    void growthStopsAtConfiguredCeiling() {
        AdaptiveEngineProperties properties = baseProperties();
        properties.setInitialLimit(200);
        properties.setGrowthCeiling(230);
        properties.setGrowthConfirmationWindows(1);
        AdaptiveDecisionEngine engine = engine(properties);

        assertEquals(210, engine.evaluate(elevatedTraffic(), normal()).getProposedLimit());
        assertEquals(220, engine.evaluate(elevatedTraffic(), normal()).getProposedLimit());
        assertEquals(230, engine.evaluate(elevatedTraffic(), normal()).getProposedLimit());

        AdaptiveDecision atCeiling = engine.evaluate(elevatedTraffic(), normal());
        assertEquals(DecisionAction.KEEP_LIMIT, atCeiling.getAction());
        assertEquals("AT_MAX_LIMIT", atCeiling.getReasonCode());
        assertEquals(230, atCeiling.getProposedLimit());
    }

    @Test
    @DisplayName("a spike without corroboration is not classified as an attack when configured")
    void spikeRequiresCorroborationWhenEnabled() {
        AdaptiveEngineProperties properties = baseProperties();
        properties.setSpikeRequiresCorroboration(true);
        AdaptiveDecisionEngine engine = engine(properties);

        AdaptiveDecision unconfirmed = engine.evaluate(spikeTraffic(), normal());
        assertEquals(DecisionAction.KEEP_LIMIT, unconfirmed.getAction());
        assertEquals("TRAFFIC_SPIKE_UNCONFIRMED", unconfirmed.getReasonCode());

        AdaptiveDecision corroborated = engine.evaluate(spikeTraffic(), anomaly());
        assertEquals(DecisionAction.DECREASE_LIMIT, corroborated.getAction());
        assertEquals("TRAFFIC_SPIKE", corroborated.getReasonCode());
    }

    @Test
    @DisplayName("growth near the integer maximum does not overflow")
    void growthNearIntegerMaxDoesNotOverflow() {
        AdaptiveEngineProperties properties = baseProperties();
        properties.setMaxLimit(Integer.MAX_VALUE);
        properties.setInitialLimit(Integer.MAX_VALUE - 5);
        properties.setIncreaseStep(10);
        properties.setGrowthConfirmationWindows(1);
        AdaptiveDecisionEngine engine = engine(properties);

        AdaptiveDecision decision = engine.evaluate(elevatedTraffic(), normal());

        assertEquals(DecisionAction.INCREASE_LIMIT, decision.getAction());
        assertEquals(Integer.MAX_VALUE, decision.getProposedLimit());
        assertTrue(decision.getProposedLimit() > 0);
    }
}
