package com.example.ratelimiter.monitoring;

import java.time.Duration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MonitoringService {
    private final TrafficMetrics trafficMetrics;
    private final EwmaCalculator ewmaCalculator;
    private final MonitoringProperties properties;

    public MonitoringService(TrafficMetrics trafficMetrics, MonitoringProperties properties) {
        this.trafficMetrics = trafficMetrics;
        this.properties = properties;
        double alpha = properties.getEwmaAlpha();
        if (alpha <= 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("monitoring.ewma-alpha must be in (0, 1]");
        }
        this.ewmaCalculator = new EwmaCalculator(alpha);
    }

    @Scheduled(initialDelayString = "${monitoring.observation-interval:30s}", fixedDelayString = "${monitoring.observation-interval:30s}")
    public void updateObservations() {
        Duration interval = properties.getObservationInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            interval = Duration.ofSeconds(30);
        }
        trafficMetrics.closeWindow(interval);
        ewmaCalculator.update(trafficMetrics.getCurrentRequestRatePerSecond());
    }

    public TrafficMetricsDto getMetrics() {
        return new TrafficMetricsDto(
                trafficMetrics.getTotalRequests(),
                trafficMetrics.getAllowedRequests(),
                trafficMetrics.getRejectedByRateLimit(),
                trafficMetrics.getErrorRate(),
                trafficMetrics.getCurrentRequestRatePerSecond(),
                ewmaCalculator.getSmoothed(),
                trafficMetrics.getObservationTimestamp()
        );
    }

    public EwmaCalculator getEwmaCalculator() {
        return ewmaCalculator;
    }

    public TrafficMetrics getTrafficMetrics() {
        return trafficMetrics;
    }
}
