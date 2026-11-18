package com.example.ratelimiter.adaptive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "adaptive-rate-limiter")
public class AdaptiveEngineProperties {
    /**
     * Upper bound for the step-based properties. It keeps the configured step
     * small enough that limit arithmetic cannot overflow even before clamping.
     */
    static final int STEP_UPPER_BOUND = 1_000_000;

    @NotNull
    @Min(1)
    private Integer minLimit = 1;

    @NotNull
    @Min(1)
    private Integer maxLimit = 1000;

    @NotNull
    @Min(1)
    private Integer initialLimit = 100;

    @NotNull
    @Min(1)
    @Max(STEP_UPPER_BOUND)
    private Integer increaseStep = 10;

    @NotNull
    @Min(1)
    @Max(STEP_UPPER_BOUND)
    private Integer decreaseStep = 10;

    /**
     * Maximum fraction of the current limit that a single decrease may remove
     * (for example 0.2 removes at most 20%). The proportional drop is rounded
     * down and a decrease is also capped by {@link #decreaseStep}; the only
     * exception is a minimum drop of one at very small limits. Together these
     * prevent one anomaly from collapsing a small limit (5 -> 1) in a single
     * evaluation.
     */
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("1.0")
    private double decreaseFraction = 0.2;

    @NotNull
    @Min(1)
    @Max(STEP_UPPER_BOUND)
    private Integer recoveryStep = 1;

    @NotNull
    @Min(1)
    private Integer recoveryWindows = 3;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double highErrorRateThreshold = 0.1;

    private double spikeRatioThreshold = 1.5;

    private double growthRatioThreshold = 1.2;

    /**
     * Number of consecutive anomalous windows required before an ML anomaly is
     * allowed to decrease the limit. A value of 1 reproduces the legacy
     * single-window reaction; the default of 2 avoids reacting to isolated
     * (potentially false-positive) anomalies.
     */
    @NotNull
    @Min(1)
    private Integer anomalyConfirmationWindows = 2;

    /**
     * Optional Isolation Forest score gate. When set, an anomaly only counts
     * when the reported score is greater than or equal to this threshold. The
     * score is model-specific and unbounded, so the gate is disabled (null) by
     * default. Non-finite or missing scores fall back to the boolean result.
     */
    private Double anomalyScoreThreshold;

    /**
     * When true, an anomaly only counts when it is corroborated by an elevated
     * traffic trend or a high error rate. This prevents decreasing the limit on
     * an anomaly while the observed traffic is calm and error-free.
     */
    private boolean anomalyRequireCorroboration = false;

    /**
     * Number of consecutive windows that must show sustained growth before the
     * limit is increased.
     */
    @NotNull
    @Min(1)
    private Integer growthConfirmationWindows = 2;

    /**
     * Optional soft ceiling for sustained growth. When unset the limit may grow
     * up to {@link #maxLimit}. When set it must lie within
     * [{@link #minLimit}, {@link #maxLimit}] and caps growth below maxLimit.
     */
    private Integer growthCeiling;

    /**
     * When true, a traffic spike only decreases the limit when corroborated by
     * an anomaly assessment or a high error rate, so a legitimate burst is not
     * automatically classified as an attack.
     */
    private boolean spikeRequiresCorroboration = false;

    public void validate() {
        if (minLimit > maxLimit) {
            throw new IllegalArgumentException("adaptive-rate-limiter.min-limit must be <= adaptive-rate-limiter.max-limit");
        }
        if (initialLimit < minLimit || initialLimit > maxLimit) {
            throw new IllegalArgumentException("adaptive-rate-limiter.initial-limit must be between min-limit and max-limit");
        }
        if (spikeRatioThreshold < 1.0) {
            throw new IllegalArgumentException("adaptive-rate-limiter.spike-ratio-threshold must be >= 1.0");
        }
        if (growthRatioThreshold < 1.0) {
            throw new IllegalArgumentException("adaptive-rate-limiter.growth-ratio-threshold must be >= 1.0");
        }
        if (decreaseFraction <= 0.0 || decreaseFraction > 1.0) {
            throw new IllegalArgumentException("adaptive-rate-limiter.decrease-fraction must be in (0, 1]");
        }
        if (recoveryStep < 1) {
            throw new IllegalArgumentException("adaptive-rate-limiter.recovery-step must be >= 1");
        }
        if (recoveryWindows < 1) {
            throw new IllegalArgumentException("adaptive-rate-limiter.recovery-windows must be >= 1");
        }
        if (anomalyConfirmationWindows == null || anomalyConfirmationWindows < 1) {
            throw new IllegalArgumentException("adaptive-rate-limiter.anomaly-confirmation-windows must be >= 1");
        }
        if (growthConfirmationWindows == null || growthConfirmationWindows < 1) {
            throw new IllegalArgumentException("adaptive-rate-limiter.growth-confirmation-windows must be >= 1");
        }
        if (growthCeiling != null && (growthCeiling < minLimit || growthCeiling > maxLimit)) {
            throw new IllegalArgumentException("adaptive-rate-limiter.growth-ceiling must be between min-limit and max-limit");
        }
    }

    // Getters and setters
    public Integer getMinLimit() { return minLimit; }
    public void setMinLimit(Integer minLimit) { this.minLimit = minLimit; }
    public Integer getMaxLimit() { return maxLimit; }
    public void setMaxLimit(Integer maxLimit) { this.maxLimit = maxLimit; }
    public Integer getInitialLimit() { return initialLimit; }
    public void setInitialLimit(Integer initialLimit) { this.initialLimit = initialLimit; }
    public Integer getIncreaseStep() { return increaseStep; }
    public void setIncreaseStep(Integer increaseStep) { this.increaseStep = increaseStep; }
    public Integer getDecreaseStep() { return decreaseStep; }
    public void setDecreaseStep(Integer decreaseStep) { this.decreaseStep = decreaseStep; }
    public double getDecreaseFraction() { return decreaseFraction; }
    public void setDecreaseFraction(double decreaseFraction) { this.decreaseFraction = decreaseFraction; }
    public Integer getRecoveryStep() { return recoveryStep; }
    public void setRecoveryStep(Integer recoveryStep) { this.recoveryStep = recoveryStep; }
    public Integer getRecoveryWindows() { return recoveryWindows; }
    public void setRecoveryWindows(Integer recoveryWindows) { this.recoveryWindows = recoveryWindows; }
    public double getHighErrorRateThreshold() { return highErrorRateThreshold; }
    public void setHighErrorRateThreshold(double highErrorRateThreshold) { this.highErrorRateThreshold = highErrorRateThreshold; }
    public double getSpikeRatioThreshold() { return spikeRatioThreshold; }
    public void setSpikeRatioThreshold(double spikeRatioThreshold) { this.spikeRatioThreshold = spikeRatioThreshold; }
    public double getGrowthRatioThreshold() { return growthRatioThreshold; }
    public void setGrowthRatioThreshold(double growthRatioThreshold) { this.growthRatioThreshold = growthRatioThreshold; }
    public Integer getAnomalyConfirmationWindows() { return anomalyConfirmationWindows; }
    public void setAnomalyConfirmationWindows(Integer anomalyConfirmationWindows) { this.anomalyConfirmationWindows = anomalyConfirmationWindows; }
    public Double getAnomalyScoreThreshold() { return anomalyScoreThreshold; }
    public void setAnomalyScoreThreshold(Double anomalyScoreThreshold) { this.anomalyScoreThreshold = anomalyScoreThreshold; }
    public boolean isAnomalyRequireCorroboration() { return anomalyRequireCorroboration; }
    public void setAnomalyRequireCorroboration(boolean anomalyRequireCorroboration) { this.anomalyRequireCorroboration = anomalyRequireCorroboration; }
    public Integer getGrowthConfirmationWindows() { return growthConfirmationWindows; }
    public void setGrowthConfirmationWindows(Integer growthConfirmationWindows) { this.growthConfirmationWindows = growthConfirmationWindows; }
    public Integer getGrowthCeiling() { return growthCeiling; }
    public void setGrowthCeiling(Integer growthCeiling) { this.growthCeiling = growthCeiling; }
    public boolean isSpikeRequiresCorroboration() { return spikeRequiresCorroboration; }
    public void setSpikeRequiresCorroboration(boolean spikeRequiresCorroboration) { this.spikeRequiresCorroboration = spikeRequiresCorroboration; }
}
