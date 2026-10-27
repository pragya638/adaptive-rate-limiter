package com.example.ratelimiter.monitoring;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "monitoring")
public class MonitoringProperties {
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax(value = "1.0", inclusive = true)
    private double ewmaAlpha = 0.3;

    @NotNull
    private Duration observationInterval = Duration.ofSeconds(30);

    public double getEwmaAlpha() {
        return ewmaAlpha;
    }

    public void setEwmaAlpha(double ewmaAlpha) {
        this.ewmaAlpha = ewmaAlpha;
    }

    public Duration getObservationInterval() {
        return observationInterval;
    }

    public void setObservationInterval(Duration observationInterval) {
        this.observationInterval = observationInterval;
    }
}
