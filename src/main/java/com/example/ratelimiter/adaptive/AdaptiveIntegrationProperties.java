package com.example.ratelimiter.adaptive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "adaptive")
public class AdaptiveIntegrationProperties {
    private boolean enabled = false;
    @Min(0)
    private long cooldownSeconds = 10;
    @Min(1)
    private long evaluationLockTtlSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public long getEvaluationLockTtlSeconds() {
        return evaluationLockTtlSeconds;
    }

    public void setEvaluationLockTtlSeconds(long evaluationLockTtlSeconds) {
        this.evaluationLockTtlSeconds = evaluationLockTtlSeconds;
    }
}
