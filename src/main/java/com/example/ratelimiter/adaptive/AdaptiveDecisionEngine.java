package com.example.ratelimiter.adaptive;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AdaptiveDecisionEngine {
    private final AdaptiveEngineProperties properties;
    private final Clock clock;
    private int currentLimit;
    private int consecutiveNormalWindows;
    private int consecutiveAnomalousWindows;
    private int consecutiveGrowthWindows;

    public AdaptiveDecisionEngine() {
        this(defaultProperties(), Clock.systemUTC());
    }

    public AdaptiveDecisionEngine(AdaptiveEngineProperties properties) {
        this(properties, Clock.systemUTC());
    }

    @Autowired
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

        if (isIdleWindow(traffic)) {
            resetStreaks();
            return keep(baseLimit, "NO_TRAFFIC",
                    "No traffic in the latest observation window - maintaining current limit.");
        }

        if (!anomaly.isAvailable()) {
            consecutiveAnomalousWindows = 0;
            consecutiveGrowthWindows = 0;
            if (isHighErrorRate(traffic)) {
                consecutiveNormalWindows = 0;
                return decrease(baseLimit, "HIGH_ERROR_NO_ANOMALY_INFO",
                        "High error rate detected; decreasing limit conservatively.");
            }
            // Without an anomaly signal we neither recover nor penalise the
            // limit; hold the recovery streak rather than resetting it.
            return keep(baseLimit, "ANOMALY_UNAVAILABLE_CONSERVATIVE_FALLBACK",
                    "Anomaly assessment unavailable - maintaining current limit (no increase).");
        }

        boolean candidate = anomaly.isAnomalous() && passesScoreGate(anomaly);
        if (candidate && properties.isAnomalyRequireCorroboration() && !isCorroborated(traffic)) {
            candidate = false;
        }
        if (candidate) {
            consecutiveAnomalousWindows++;
        } else {
            consecutiveAnomalousWindows = 0;
        }

        if (candidate && consecutiveAnomalousWindows >= properties.getAnomalyConfirmationWindows()) {
            consecutiveNormalWindows = 0;
            consecutiveGrowthWindows = 0;
            return decrease(baseLimit, "ANOMALY_DETECTED",
                    "Confirmed anomalous traffic - decreasing limit gradually for safety.");
        }

        if (isHighErrorRate(traffic)) {
            consecutiveNormalWindows = 0;
            consecutiveGrowthWindows = 0;
            return decrease(baseLimit, "HIGH_ERROR_RATE",
                    "High error rate observed - decreasing limit gradually.");
        }

        Double currentRate = traffic.getCurrentRequestRate();
        Double ewmaRate = traffic.getEwmaRequestRate();
        boolean rateAvailable = isUsableRate(currentRate) && isUsableRate(ewmaRate) && ewmaRate > 0.0;
        double ratio = rateAvailable ? currentRate / ewmaRate : 0.0;

        if (rateAvailable && ratio >= properties.getSpikeRatioThreshold()) {
            boolean corroborated = anomaly.isAnomalous() || isHighErrorRate(traffic);
            if (!properties.isSpikeRequiresCorroboration() || corroborated) {
                resetStreaks();
                return decrease(baseLimit, "TRAFFIC_SPIKE",
                        "Sudden traffic spike detected - decreasing limit as a precaution.");
            }
            consecutiveGrowthWindows = 0;
            return keep(baseLimit, "TRAFFIC_SPIKE_UNCONFIRMED",
                    "Traffic burst without anomaly or error corroboration - maintaining current limit.");
        }

        if (candidate) {
            return keep(baseLimit, "ANOMALY_PENDING",
                    "Anomaly detected but not yet confirmed - monitoring before decreasing the limit.");
        }

        if (rateAvailable && ratio >= properties.getGrowthRatioThreshold()) {
            consecutiveGrowthWindows++;
            if (consecutiveGrowthWindows >= properties.getGrowthConfirmationWindows()) {
                return increase(baseLimit, properties.getIncreaseStep(), growthCeiling(),
                        "SUSTAINED_GROWTH",
                        "Sustained traffic growth observed - increasing limit within bounds.");
            }
            return keep(baseLimit, "GROWTH_PENDING",
                    "Traffic growth observed but not yet sustained - monitoring before increasing the limit.");
        }
        consecutiveGrowthWindows = 0;

        consecutiveNormalWindows++;
        if (baseLimit < properties.getInitialLimit()
                && consecutiveNormalWindows >= properties.getRecoveryWindows()) {
            int proposed = (int) Math.min((long) properties.getInitialLimit(),
                    clamp((long) baseLimit + properties.getRecoveryStep()));
            if (proposed > baseLimit) {
                currentLimit = proposed;
                return new AdaptiveDecision(DecisionAction.INCREASE_LIMIT, baseLimit, proposed, "RECOVERY",
                        "Traffic has been normal for several windows - recovering the limit gradually.",
                        Instant.now(clock));
            }
        }

        return keep(baseLimit, "NORMAL_TRAFFIC", "Normal traffic patterns - maintaining current limit.");
    }

    private AdaptiveDecision keep(int baseLimit, String reasonCode, String explanation) {
        currentLimit = baseLimit;
        return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, reasonCode,
                explanation, Instant.now(clock));
    }

    private AdaptiveDecision decrease(int baseLimit, String reasonCode, String explanation) {
        int decrease = calculateDecrease(baseLimit);
        int proposed = clamp((long) baseLimit - decrease);
        if (proposed == baseLimit) {
            currentLimit = baseLimit;
            return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "AT_MIN_LIMIT",
                    "Limit is already at the configured minimum.", Instant.now(clock));
        }
        currentLimit = proposed;
        return new AdaptiveDecision(DecisionAction.DECREASE_LIMIT, baseLimit, proposed, reasonCode,
                explanation, Instant.now(clock));
    }

    private AdaptiveDecision increase(int baseLimit, int step, int ceiling, String reasonCode, String explanation) {
        int proposed = clampUpTo((long) baseLimit + step, ceiling);
        if (proposed == baseLimit) {
            currentLimit = baseLimit;
            return new AdaptiveDecision(DecisionAction.KEEP_LIMIT, baseLimit, baseLimit, "AT_MAX_LIMIT",
                    "Limit is already at the configured maximum.", Instant.now(clock));
        }
        currentLimit = proposed;
        return new AdaptiveDecision(DecisionAction.INCREASE_LIMIT, baseLimit, proposed, reasonCode,
                explanation, Instant.now(clock));
    }

    /**
     * Bounded decrease: the drop is the smaller of the configured step and the
     * configured fraction of the current limit, and never less than one. The
     * fraction is rounded down so a single decrease can never remove more than
     * the configured proportion (except the unavoidable minimum drop of one at
     * very small limits). This keeps small limits (for example 5) from
     * collapsing to the minimum after a single anomaly while still allowing
     * repeated anomalies to reduce the limit steadily.
     */
    private int calculateDecrease(int baseLimit) {
        int proportional = (int) Math.floor(baseLimit * properties.getDecreaseFraction());
        return Math.max(1, Math.min(properties.getDecreaseStep(), proportional));
    }

    private boolean isHighErrorRate(TrafficAssessment traffic) {
        Double errorRate = traffic.getEffectiveErrorRate();
        if (errorRate == null || Double.isNaN(errorRate) || Double.isInfinite(errorRate)) {
            return false;
        }
        return errorRate >= properties.getHighErrorRateThreshold();
    }

    /**
     * Whether an ML anomaly should count as a candidate. When a score
     * threshold is configured the score must meet it, but a missing or
     * non-finite score falls back to the boolean result rather than being
     * treated as either an anomaly or a clean window.
     */
    private boolean passesScoreGate(AnomalyAssessment anomaly) {
        Double threshold = properties.getAnomalyScoreThreshold();
        if (threshold == null) {
            return true;
        }
        Double score = anomaly.getAnomalyScore();
        if (score == null || Double.isNaN(score) || Double.isInfinite(score)) {
            return true;
        }
        return score >= threshold;
    }

    /**
     * Corroboration for an anomaly: either the error rate is already high or
     * the traffic trend is elevated. An increasing trend on its own is never
     * treated as proof of an attack; it only corroborates an existing anomaly
     * assessment.
     */
    private boolean isCorroborated(TrafficAssessment traffic) {
        if (isHighErrorRate(traffic)) {
            return true;
        }
        Double currentRate = traffic.getCurrentRequestRate();
        Double ewmaRate = traffic.getEwmaRequestRate();
        if (isUsableRate(currentRate) && isUsableRate(ewmaRate) && ewmaRate > 0.0) {
            return (currentRate / ewmaRate) >= properties.getGrowthRatioThreshold();
        }
        return false;
    }

    private boolean isUsableRate(Double rate) {
        return rate != null && !Double.isNaN(rate) && !Double.isInfinite(rate);
    }

    private boolean isIdleWindow(TrafficAssessment traffic) {
        Long windowCount = traffic.getWindowRequestCount();
        Double currentRate = traffic.getCurrentRequestRate();
        if (windowCount != null) {
            return windowCount == 0L && (currentRate == null || currentRate <= 0.0);
        }
        return currentRate != null && currentRate <= 0.0
                && (traffic.getTotalRequests() == null || traffic.getTotalRequests() == 0L);
    }

    private int growthCeiling() {
        Integer ceiling = properties.getGrowthCeiling();
        if (ceiling == null) {
            return properties.getMaxLimit();
        }
        return Math.min(ceiling, properties.getMaxLimit());
    }

    private int clamp(long value) {
        if (value < properties.getMinLimit()) {
            return properties.getMinLimit();
        }
        if (value > properties.getMaxLimit()) {
            return properties.getMaxLimit();
        }
        return (int) value;
    }

    private int clampUpTo(long value, int ceiling) {
        int lower = properties.getMinLimit();
        int upper = Math.min(ceiling, properties.getMaxLimit());
        if (upper < lower) {
            upper = lower;
        }
        if (value < lower) {
            return lower;
        }
        if (value > upper) {
            return upper;
        }
        return (int) value;
    }

    private void resetStreaks() {
        consecutiveNormalWindows = 0;
        consecutiveAnomalousWindows = 0;
        consecutiveGrowthWindows = 0;
    }

    public synchronized int getCurrentLimit() {
        return currentLimit;
    }

    public synchronized void reset() {
        this.currentLimit = clamp(properties.getInitialLimit());
        resetStreaks();
    }
}
