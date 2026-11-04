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
        properties.setAdjustmentThreshold(0.05);
        properties.setEnableOscillationGuard(true);
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
        TrafficAssessment traffic = new TrafficAssessment(100.0, 50.0, 0.01, 2000L, 1980L, 20L);
        AnomalyAssessment anomaly = new AnomalyAssessment(false, -0.2, "normal", "IsolationForest");
        AdaptiveDecision decision = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.INCREASE_LIMIT, decision.getAction());
        assertEquals(110, decision.getProposedLimit());
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
        assertEquals(10, decision.getProposedLimit());
        // Next time already at minimum
        AdaptiveDecision decision2 = engine.evaluate(traffic, anomaly);
        assertEquals(DecisionAction.KEEP_LIMIT, decision2.getAction());
        assertEquals(10, decision2.getProposedLimit());
    }

    @Test
    void limitReachesMaximum() {
        properties.setInitialLimit(990);
        engine = new AdaptiveDecisionEngine(properties, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        TrafficAssessment traffic = new TrafficAssessment(2000.0, 100.0, 0.01, 10000L, 9900L, 100L);
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
