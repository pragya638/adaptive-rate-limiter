package com.example.ratelimiter.adaptive.mapping;

import org.springframework.stereotype.Component;

import com.example.ratelimiter.adaptive.TrafficAssessment;
import com.example.ratelimiter.adaptive.client.TrafficFeaturesRequest;

@Component
public class MetricsToFeaturesMapper {
    public TrafficFeaturesRequest toRequest(TrafficAssessment traffic) {
        if (traffic == null) {
            return null;
        }
        double requestRate = traffic.getCurrentRequestRate() == null ? 0.0 : traffic.getCurrentRequestRate();
        double ewma = traffic.getEwmaRequestRate() == null ? 0.0 : traffic.getEwmaRequestRate();
        double errorRate = traffic.getErrorRate() == null ? 0.0 : traffic.getErrorRate();
        long requestCount = traffic.getTotalRequests() == null ? 0L : traffic.getTotalRequests();
        if (Double.isNaN(requestRate) || Double.isInfinite(requestRate) || requestRate < 0.0) {
            requestRate = 0.0;
        }
        if (Double.isNaN(ewma) || Double.isInfinite(ewma) || ewma < 0.0) {
            ewma = 0.0;
        }
        if (Double.isNaN(errorRate) || Double.isInfinite(errorRate) || errorRate < 0.0) {
            errorRate = 0.0;
        }
        if (errorRate > 1.0) {
            errorRate = 1.0;
        }
        if (requestCount < 0) {
            requestCount = 0L;
        }
        double burstRatio = 0.0;
        if (ewma > 0.0) {
            burstRatio = requestRate / ewma;
        } else if (requestRate > 0.0) {
            burstRatio = Math.max(1.0, requestRate);
        } else {
            burstRatio = 0.0;
        }
        if (Double.isNaN(burstRatio) || Double.isInfinite(burstRatio) || burstRatio < 0.0) {
            burstRatio = 0.0;
        }
        return new TrafficFeaturesRequest(requestRate, errorRate, burstRatio, requestCount);
    }
}
