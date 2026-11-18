package com.example.ratelimiter.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdaptiveDecisionEngineTest {
    private AdaptiveEngineProperties properties;
    private AdaptiveDecisionEngine engine;

    @BeforeEach
    void setUp() {
        properties = new AdaptiveEngineProperties();
        properties.setMinLimit(10);
        properties.setMaxLimit(1000);
        properties.setInitialLimit(100);
        properties.setIncreaseStep(10);
        properties.setDecreaseStep(20);
        properties.setHighErrorRateThreshold(0.1);
        properties.setSpikeRatioThreshold(1.5);
        properties.setGrowthRatioThreshold(1.2);
        properties.setAnomalyConfirmationWindows(1);
        properties.setGrowthConfirmationWindows(1);
        engine = new AdaptiveDecisionEngine(properties, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void normalTrafficNoAnomalyKeepsLimit() {
        TrafficAssessment traffic = new TrafficAssessment(50.0, 48.0, 0.01, 1000L, 990L, 10L);
        AnomalyAssessment anomaly = new AnomalyAssessment(false, -0.1, "normal", "IsolationForest");
        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.KEEP_LIMIT, decision.getAction());
        assertEquals(100, decision.getProposedLimit());
    }

    @Test
    void sustainedTrafficGrowthIncreasesLimit() {
        TrafficAssessment traffic = new TrafficAssessment(65.0, 50.0, 0.01, 2000L, 1980L, 20L);
        AnomalyAssessment anomaly = new AnomalyAssessment(false, -0.2, "normal", "IsolationForest");
        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.INCREASE_LIMIT, decision.getAction());
        assertEquals(110, decision.getProposedLimit());
    }

    @Test
    void sustainedGrowthIsAppliedAtArbitraryLimit() {
        properties.setInitialLimit(50);
        engine = new AdaptiveDecisionEngine(properties, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        TrafficAssessment traffic = new TrafficAssessment(65.0, 50.0, 0.01, 650L, 640L, 10L);
        AnomalyAssessment anomaly = new AnomalyAssessment(false, -0.2, "normal", "IsolationForest");

        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);

        assertEquals(DecisionAction.INCREASE_LIMIT, decision.getAction());
        assertEquals(60, decision.getProposedLimit());
    }

    @Test
    void threeTimesSpikeAtNonDefaultLimitDecreasesLimit() {
        properties.setMinLimit(1);
        properties.setInitialLimit(5);
        properties.setDecreaseStep(1);
        engine = new AdaptiveDecisionEngine(properties, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        TrafficAssessment traffic = new TrafficAssessment(15.0, 5.0, 0.01, 75L, 74L, 1L);
        AnomalyAssessment anomaly = new AnomalyAssessment(false, -0.1, "normal", "IsolationForest");

        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);

        assertEquals(DecisionAction.DECREASE_LIMIT, decision.getAction());
        assertEquals("TRAFFIC_SPIKE", decision.getReasonCode());
        assertEquals(4, decision.getProposedLimit());
    }

    @Test
    void idleWindowNeverReactsToAnomalyAssessment() {
        TrafficAssessment traffic = new TrafficAssessment(
                0.0, 5.0, 0.0, 30L, 5L, 25L, 0L, 0.0);
        AnomalyAssessment anomaly = new AnomalyAssessment(true, 0.9, "anomaly", "IsolationForest");

        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);

        assertEquals(DecisionAction.KEEP_LIMIT, decision.getAction());
        assertEquals("NO_TRAFFIC", decision.getReasonCode());
        assertEquals(100, decision.getProposedLimit());
    }

    @Test
    void rateLimitRejectionsAloneDoNotDecreaseLimit() {
        TrafficAssessment traffic = new TrafficAssessment(50.0, 50.0, 0.5, 1000L, 500L, 500L, 100L, 0.0);
        AnomalyAssessment anomaly = new AnomalyAssessment(false, -0.1, "normal", "IsolationForest");

        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);

        assertEquals(DecisionAction.KEEP_LIMIT, decision.getAction());
        assertEquals("NORMAL_TRAFFIC", decision.getReasonCode());
    }

    @Test
    void highClientErrorRateStillDecreasesLimit() {
        TrafficAssessment traffic = new TrafficAssessment(50.0, 50.0, 0.01, 1000L, 750L, 0L, 100L, 0.25);
        AnomalyAssessment anomaly = new AnomalyAssessment(false, -0.1, "normal", "IsolationForest");

        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);

        assertEquals(DecisionAction.DECREASE_LIMIT, decision.getAction());
        assertEquals("HIGH_ERROR_RATE", decision.getReasonCode());
        assertEquals(80, decision.getProposedLimit());
    }

    @Test
    void suddenTrafficSpikeDecreasesLimit() {
        TrafficAssessment traffic = new TrafficAssessment(150.0, 50.0, 0.01, 1500L, 1485L, 15L);
        AnomalyAssessment anomaly = new AnomalyAssessment(false, -0.1, "normal", "IsolationForest");
        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.DECREASE_LIMIT, decision.getAction());
        assertEquals(80, decision.getProposedLimit());
    }

    @Test
    void anomalousTrafficAssessmentDecreasesLimit() {
        TrafficAssessment traffic = new TrafficAssessment(60.0, 55.0, 0.01, 1000L, 990L, 10L);
        AnomalyAssessment anomaly = new AnomalyAssessment(true, 0.5, "anomaly", "IsolationForest");
        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.DECREASE_LIMIT, decision.getAction());
        assertEquals(80, decision.getProposedLimit());
    }

    @Test
    void highErrorRateDecreasesLimit() {
        TrafficAssessment traffic = new TrafficAssessment(50.0, 50.0, 0.2, 1000L, 800L, 200L);
        AnomalyAssessment anomaly = new AnomalyAssessment(false, -0.1, "normal", "IsolationForest");
        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.DECREASE_LIMIT, decision.getAction());
        assertEquals(80, decision.getProposedLimit());
    }

    @Test
    void limitReachesMinimum() {
        properties.setInitialLimit(15);
        engine = new AdaptiveDecisionEngine(properties, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        TrafficAssessment traffic = new TrafficAssessment(50.0, 10.0, 0.01, 500L, 495L, 5L);
        AnomalyAssessment anomaly = new AnomalyAssessment(true, 0.8, "anomaly", "IsolationForest");
        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.DECREASE_LIMIT, decision.getAction());
        assertTrue(decision.getProposedLimit() >= properties.getMinLimit());
        assertEquals(12, decision.getProposedLimit());
        // Repeated anomalies continue the bounded decrease to the minimum.
        AdaptiveDecision decision2 = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.DECREASE_LIMIT, decision2.getAction());
        assertEquals(10, decision2.getProposedLimit());
        AdaptiveDecision decision3 = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.KEEP_LIMIT, decision3.getAction());
        assertEquals(10, decision3.getProposedLimit());
    }

    @Test
    void isolatedAnomalyAtSmallLimitDecreasesGradually() {
        properties.setMinLimit(1);
        properties.setMaxLimit(50);
        properties.setInitialLimit(5);
        properties.setDecreaseStep(10);
        properties.setDecreaseFraction(0.2);
        engine = new AdaptiveDecisionEngine(properties, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        TrafficAssessment traffic = new TrafficAssessment(50.0, 50.0, 0.01, 1000L, 990L, 10L);
        AnomalyAssessment anomaly = new AnomalyAssessment(true, 0.5, "anomaly", "IsolationForest");

        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);

        assertEquals(DecisionAction.DECREASE_LIMIT, decision.getAction());
        assertEquals(4, decision.getProposedLimit());
        assertNotEquals(1, decision.getProposedLimit());
    }

    @Test
    void repeatedAnomaliesDecreaseStepByStepToMinimum() {
        properties.setMinLimit(1);
        properties.setMaxLimit(50);
        properties.setInitialLimit(5);
        properties.setDecreaseStep(10);
        properties.setDecreaseFraction(0.2);
        engine = new AdaptiveDecisionEngine(properties, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        TrafficAssessment traffic = new TrafficAssessment(50.0, 50.0, 0.01, 1000L, 990L, 10L);
        AnomalyAssessment anomaly = new AnomalyAssessment(true, 0.5, "anomaly", "IsolationForest");

        assertEquals(4, engine.evaluate(traffic, anomaly).getProposedLimit());
        assertEquals(3, engine.evaluate(traffic, anomaly).getProposedLimit());
        assertEquals(2, engine.evaluate(traffic, anomaly).getProposedLimit());
        assertEquals(1, engine.evaluate(traffic, anomaly).getProposedLimit());
        AdaptiveDecision atMinimum = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.KEEP_LIMIT, atMinimum.getAction());
        assertEquals("AT_MIN_LIMIT", atMinimum.getReasonCode());
        assertEquals(1, atMinimum.getProposedLimit());
    }

    @Test
    void decreaseNeverExceedsTheConfiguredFraction() {
        properties.setMinLimit(1);
        properties.setMaxLimit(1000);
        properties.setDecreaseStep(1000);
        properties.setDecreaseFraction(0.2);
        AnomalyAssessment anomaly = new AnomalyAssessment(true, 0.5, "anomaly", "IsolationForest");
        TrafficAssessment traffic = new TrafficAssessment(50.0, 50.0, 0.01, 1000L, 990L, 10L);

        for (int base : new int[] { 10, 15, 20, 25, 30, 50, 100 }) {
            properties.setInitialLimit(base);
            AdaptiveDecisionEngine local = new AdaptiveDecisionEngine(properties,
                    Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

            AdaptiveDecision decision = local.evaluate(traffic, anomaly);
            int expectedDecrease = Math.max(1, (int) Math.floor(base * 0.2));

            assertEquals(base - expectedDecrease, decision.getProposedLimit(),
                    "decrease from " + base + " must stay within the configured fraction");
        }
    }

    @Test
    void recoveryFromALargeLimitProceedsGradually() {
        properties.setMinLimit(1);
        properties.setMaxLimit(1000);
        properties.setInitialLimit(100);
        properties.setDecreaseStep(1);
        properties.setDecreaseFraction(0.01);
        properties.setRecoveryStep(1);
        properties.setRecoveryWindows(1);
        engine = new AdaptiveDecisionEngine(properties,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        TrafficAssessment traffic = new TrafficAssessment(50.0, 50.0, 0.01, 1000L, 990L, 10L);

        assertEquals(99, engine.evaluate(traffic,
                new AnomalyAssessment(true, 0.5, "anomaly", "IsolationForest")).getProposedLimit());

        AdaptiveDecision recovery = engine.evaluate(traffic,
                new AnomalyAssessment(false, -0.1, "normal", "IsolationForest"));
        assertEquals(DecisionAction.INCREASE_LIMIT, recovery.getAction());
        assertEquals("RECOVERY", recovery.getReasonCode());
        assertEquals(100, recovery.getProposedLimit());
    }

    @Test
    void normalWindowsRecoverLimitGraduallyUpToInitialLimit() {
        properties.setMinLimit(1);
        properties.setMaxLimit(50);
        properties.setInitialLimit(5);
        properties.setDecreaseStep(10);
        properties.setDecreaseFraction(0.2);
        properties.setRecoveryStep(1);
        properties.setRecoveryWindows(2);
        engine = new AdaptiveDecisionEngine(properties, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        TrafficAssessment anomalousTraffic = new TrafficAssessment(50.0, 50.0, 0.01, 1000L, 990L, 10L);
        AnomalyAssessment anomaly = new AnomalyAssessment(true, 0.5, "anomaly", "IsolationForest");
        TrafficAssessment normalTraffic = new TrafficAssessment(50.0, 50.0, 0.0, 1000L, 1000L, 0L, 100L, 0.0);
        AnomalyAssessment normal = new AnomalyAssessment(false, -0.1, "normal", "IsolationForest");

        engine.evaluate(anomalousTraffic, anomaly);
        engine.evaluate(anomalousTraffic, anomaly);
        assertEquals(3, engine.getCurrentLimit());

        AdaptiveDecision firstNormal = engine.evaluate(normalTraffic, normal);
        assertEquals(DecisionAction.KEEP_LIMIT, firstNormal.getAction());
        assertEquals(3, firstNormal.getProposedLimit());

        AdaptiveDecision secondNormal = engine.evaluate(normalTraffic, normal);
        assertEquals(DecisionAction.INCREASE_LIMIT, secondNormal.getAction());
        assertEquals("RECOVERY", secondNormal.getReasonCode());
        assertEquals(4, secondNormal.getProposedLimit());

        AdaptiveDecision thirdNormal = engine.evaluate(normalTraffic, normal);
        assertEquals(DecisionAction.INCREASE_LIMIT, thirdNormal.getAction());
        assertEquals(5, thirdNormal.getProposedLimit());

        AdaptiveDecision atInitial = engine.evaluate(normalTraffic, normal);
        assertEquals(DecisionAction.KEEP_LIMIT, atInitial.getAction());
        assertEquals("NORMAL_TRAFFIC", atInitial.getReasonCode());
        assertEquals(5, atInitial.getProposedLimit());
    }

    @Test
    void limitReachesMaximum() {
        properties.setInitialLimit(990);
        engine = new AdaptiveDecisionEngine(properties, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        TrafficAssessment traffic = new TrafficAssessment(130.0, 100.0, 0.01, 10000L, 9900L, 100L);
        AnomalyAssessment anomaly = new AnomalyAssessment(false, -0.3, "normal", "IsolationForest");
        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.INCREASE_LIMIT, decision.getAction());
        assertEquals(1000, decision.getProposedLimit());
        AdaptiveDecision decision2 = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.KEEP_LIMIT, decision2.getAction());
        assertEquals(1000, decision2.getProposedLimit());
    }

    @Test
    void missingOrInvalidMetricsHandledSafely() {
        TrafficAssessment traffic = new TrafficAssessment(null, null, null, null, null, null);
        AnomalyAssessment anomaly = new AnomalyAssessment(false, -0.1, "normal", "IsolationForest");
        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);
        assertNotNull(decision);
        assertEquals(DecisionAction.KEEP_LIMIT, decision.getAction());
    }

    @Test
    void missingAnomalyServiceResponseConservativeFallback() {
        TrafficAssessment traffic = new TrafficAssessment(50.0, 48.0, 0.01, 1000L, 990L, 10L);
        AnomalyAssessment anomaly = AnomalyAssessment.unavailable();
        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.KEEP_LIMIT, decision.getAction());
        assertEquals("ANOMALY_UNAVAILABLE_CONSERVATIVE_FALLBACK", decision.getReasonCode());
    }

    @Test
    void noIncreaseWhenAnomalyServiceUnavailable() {
        properties.setInitialLimit(100);
        engine = new AdaptiveDecisionEngine(properties, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        TrafficAssessment traffic = new TrafficAssessment(2000.0, 100.0, 0.01, 10000L, 9900L, 100L); // would be growth
        AnomalyAssessment anomaly = AnomalyAssessment.unavailable();
        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.KEEP_LIMIT, decision.getAction());
        assertEquals(100, decision.getProposedLimit());
    }

    @Test
    void repeatedObservationsStability() {
        TrafficAssessment traffic = new TrafficAssessment(60.0, 50.0, 0.01, 1200L, 1188L, 12L);
        AnomalyAssessment anomaly = new AnomalyAssessment(false, -0.1, "normal", "IsolationForest");
        AdaptiveDecision d1 = engine.evaluate(traffic, anomaly);
        AdaptiveDecision d2 = engine.evaluate(traffic, anomaly);
        assertEquals(d1.getAction(), d2.getAction());
    }
}
