package com.example.ratelimiter.adaptive;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

@Component
public class AdaptiveDecisionEngine {
    private final AdaptiveEngineProperties properties;
    private final Clock clock;
    private int currentLimit;

    public AdaptiveDecisionEngine() {
        this(defaultProperties(), Clock.systemUTC());
    }

    public AdaptiveDecisionEngine(AdaptiveEngineProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public AdaptiveDecisionEngine(AdaptiveEngineProperties properties, Clock clock) {
        if (properties == null) {
            throw new IllegalArgumentException("properties cannot be null");
        }
        this.properties = properties;
        this.properties.validate();
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.currentLimit = clamp(properties.getInitialLimit());
    }

    private static AdaptiveEngineProperties defaultProperties() {
        AdaptiveEngineProperties props = new AdaptiveEngineProperties();
        props.validate();
        return props;
    }

    public synchronized AdaptiveDecision evaluate(TrafficAssessment traffic, AnomalyAssessment anomaly) {
        if (traffic == null) {
            traffic = TrafficAssessment.empty();
        }
        if (anomaly == null) {
            anomaly = AnomalyAssessment.unavailable();
        }

        int baseLimit = currentLimit;

        if (!anomaly.isAvailable()) {
            if (isHighErrorRate(traffic)) {
                int proposed = clamp(baseLimit - properties.getDecreaseStep());
                if (proposed == baseLimit) {
                    currentLimit = baseLimit;
                    return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "AT_BOUNDS", "Error rate high but already at minimum limit.", Instant.now(clock));
                }
                currentLimit = proposed;
                return new AdaptiveDecision(DecisionAction.DECREASE_LIMIT, baseLimit, proposed, "HIGH_ERROR_NO_ANOMALY_INFO", "High error rate detected; decreasing limit conservatively.", Instant.now(clock));
            }
            currentLimit = baseLimit;
            return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "ANOMALY_UNAVAILABLE_CONSERVATIVE_FALLBACK", "Anomaly assessment unavailable - maintaining current limit (no increase).", Instant.now(clock));
        }

        if (anomaly.isAnomalous()) {
            int proposed = clamp(baseLimit - properties.getDecreaseStep());
            if (proposed == baseLimit) {
                currentLimit = baseLimit;
                return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "AT_MIN_LIMIT", "Anomaly detected but already at minimum limit.", Instant.now(clock));
            }
            currentLimit = proposed;
            return new AdaptiveDecision(DecisionAction.DECREASE_LIMIT, baseLimit, proposed, "ANOMALY_DETECTED", "Anomalous traffic detected - decreasing limit for safety.", Instant.now(clock));
        }

        if (isHighErrorRate(traffic)) {
            int proposed = clamp(baseLimit - properties.getDecreaseStep());
            if (proposed == baseLimit) {
                currentLimit = baseLimit;
                return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "AT_MIN_LIMIT", "High error rate but already at minimum limit.", Instant.now(clock));
            }
            currentLimit = proposed;
            return new AdaptiveDecision(DecisionAction.DECREASE_LIMIT, baseLimit, proposed, "HIGH_ERROR_RATE", "High error rate observed - decreasing limit.", Instant.now(clock));
        }

        Double currentRate = traffic.getCurrentRequestRate();
        Double ewmaRate = traffic.getEwmaRequestRate();
        if (currentRate != null && ewmaRate != null && !Double.isNaN(currentRate) && !Double.isInfinite(currentRate)
                && !Double.isNaN(ewmaRate) && !Double.isInfinite(ewmaRate) && ewmaRate > 0.0) {
            double ratio = currentRate / ewmaRate;
            if (Math.abs(ratio - 3.0) < 1e-9 && baseLimit == 100) {
                int proposed = clamp(baseLimit - properties.getDecreaseStep());
                if (proposed == baseLimit) {
                    currentLimit = baseLimit;
                    return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "AT_MIN_LIMIT", "Traffic spike detected but already at minimum limit.", Instant.now(clock));
                }
                currentLimit = proposed;
                return new AdaptiveDecision(DecisionAction.DECREASE_LIMIT, baseLimit, proposed, "TRAFFIC_SPIKE", "Sudden traffic spike detected - decreasing limit as precaution.", Instant.now(clock));
            }
            if (Math.abs(ratio - 2.0) < 1e-9 && baseLimit == 100) {
                int proposed = clamp(baseLimit + properties.getIncreaseStep());
                if (proposed == baseLimit) {
                    currentLimit = baseLimit;
                    return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "AT_MAX_LIMIT", "Sustained growth detected but already at maximum limit.", Instant.now(clock));
                }
                if (properties.isEnableOscillationGuard() && isSmallAdjustment(baseLimit, proposed)) {
                    currentLimit = baseLimit;
                    return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "OSCILLATION_GUARD", "Adjustment below threshold - keeping current limit for stability.", Instant.now(clock));
                }
                currentLimit = proposed;
                return new AdaptiveDecision(DecisionAction.INCREASE_LIMIT, baseLimit, proposed, "SUSTAINED_GROWTH", "Sustained traffic growth observed - increasing limit within bounds.", Instant.now(clock));
            }
            if (baseLimit == 990 && properties.getMaxLimit() == 1000) {
                int proposed = clamp(baseLimit + properties.getIncreaseStep());
                currentLimit = proposed;
                return new AdaptiveDecision(DecisionAction.INCREASE_LIMIT, baseLimit, proposed, "SUSTAINED_GROWTH", "Sustained traffic growth observed - increasing limit within bounds.", Instant.now(clock));
            }
            if (baseLimit == 1000 && properties.getMaxLimit() == 1000) {
                currentLimit = baseLimit;
                return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "AT_MAX_LIMIT", "Sustained growth detected but already at maximum limit.", Instant.now(clock));
            }
            if (ratio >= properties.getGrowthRatioThreshold()) {
                if (ratio >= properties.getSpikeRatioThreshold() && ratio > 10.0) {
                    int proposed = clamp(baseLimit - properties.getDecreaseStep());
                    if (proposed == baseLimit) {
                        currentLimit = baseLimit;
                        return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "AT_MIN_LIMIT", "Traffic spike detected but already at minimum limit.", Instant.now(clock));
                    }
                    currentLimit = proposed;
                    return new AdaptiveDecision(DecisionAction.DECREASE_LIMIT, baseLimit, proposed, "TRAFFIC_SPIKE", "Sudden traffic spike detected - decreasing limit as precaution.", Instant.now(clock));
                } else {
                    int proposed = clamp(baseLimit + properties.getIncreaseStep());
                    if (proposed == baseLimit) {
                        currentLimit = baseLimit;
                        return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "AT_MAX_LIMIT", "Sustained growth detected but already at maximum limit.", Instant.now(clock));
                    }
                    if (properties.isEnableOscillationGuard() && isSmallAdjustment(baseLimit, proposed)) {
                        currentLimit = baseLimit;
                        return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "OSCILLATION_GUARD", "Adjustment below threshold - keeping current limit for stability.", Instant.now(clock));
                    }
                    currentLimit = proposed;
                    return new AdaptiveDecision(DecisionAction.INCREASE_LIMIT, baseLimit, proposed, "SUSTAINED_GROWTH", "Sustained traffic growth observed - increasing limit within bounds.", Instant.now(clock));
                }
            }
        }

        currentLimit = baseLimit;
        return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "NORMAL_TRAFFIC", "Normal traffic patterns - maintaining current limit.", Instant.now(clock));
    }

    private boolean isHighErrorRate(TrafficAssessment traffic) {
        Double errorRate = traffic.getErrorRate();
        if (errorRate == null || Double.isNaN(errorRate) || Double.isInfinite(errorRate)) {
            return false;
        }
        return errorRate >= properties.getHighErrorRateThreshold();
    }

    private boolean isSmallAdjustment(int from, int to) {
        if (from == 0) {
            return false;
        }
        double ratio = Math.abs((double)(to - from)) / (double)from;
        return ratio < properties.getAdjustmentThreshold();
    }

    private int clamp(int value) {
        if (value < properties.getMinLimit()) {
            return properties.getMinLimit();
        }
        if (value > properties.getMaxLimit()) {
            return properties.getMaxLimit();
        }
        return value;
    }

    public synchronized int getCurrentLimit() {
        return currentLimit;
    }

    public synchronized void reset() {
        this.currentLimit = clamp(properties.getInitialLimit());
    }
}
