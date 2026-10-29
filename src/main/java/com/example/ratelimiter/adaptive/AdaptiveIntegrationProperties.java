package com.example.ratelimiter.adaptive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "adaptive")
public class AdaptiveIntegrationProperties {
    private boolean enabled = false;
    @Min(1)
    private long evaluationIntervalSeconds = 30;
    @Min(0)
    private long cooldownSeconds = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getEvaluationIntervalSeconds() {
        return evaluationIntervalSeconds;
    }

    public void setEvaluationIntervalSeconds(long evaluationIntervalSeconds) {
        this.evaluationIntervalSeconds = evaluationIntervalSeconds;
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }
}
