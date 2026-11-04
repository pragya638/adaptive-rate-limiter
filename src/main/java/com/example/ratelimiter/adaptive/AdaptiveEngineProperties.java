package com.example.ratelimiter.adaptive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "adaptive-rate-limiter")
public class AdaptiveEngineProperties {
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
    private Integer increaseStep = 10;

    @NotNull
    @Min(1)
    private Integer decreaseStep = 10;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double highErrorRateThreshold = 0.1;

    private double spikeRatioThreshold = 1.5;

    private double growthRatioThreshold = 1.2;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double adjustmentThreshold = 0.05;

    private boolean enableOscillationGuard = true;

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
    public double getHighErrorRateThreshold() { return highErrorRateThreshold; }
    public void setHighErrorRateThreshold(double highErrorRateThreshold) { this.highErrorRateThreshold = highErrorRateThreshold; }
    public double getSpikeRatioThreshold() { return spikeRatioThreshold; }
    public void setSpikeRatioThreshold(double spikeRatioThreshold) { this.spikeRatioThreshold = spikeRatioThreshold; }
    public double getGrowthRatioThreshold() { return growthRatioThreshold; }
    public void setGrowthRatioThreshold(double growthRatioThreshold) { this.growthRatioThreshold = growthRatioThreshold; }
    public double getAdjustmentThreshold() { return adjustmentThreshold; }
    public void setAdjustmentThreshold(double adjustmentThreshold) { this.adjustmentThreshold = adjustmentThreshold; }
    public boolean isEnableOscillationGuard() { return enableOscillationGuard; }
    public void setEnableOscillationGuard(boolean enableOscillationGuard) { this.enableOscillationGuard = enableOscillationGuard; }
}
