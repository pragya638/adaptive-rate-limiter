package com.example.ratelimiter.adaptive;

public class TrafficAssessment {
    private final Double currentRequestRate;
    private final Double ewmaRequestRate;
    private final Double errorRate;
    private final Long totalRequests;
    private final Long allowedRequests;
    private final Long rejectedRequests;

    public TrafficAssessment(Double currentRequestRate, Double ewmaRequestRate, Double errorRate,
            Long totalRequests, Long allowedRequests, Long rejectedRequests) {
        this.currentRequestRate = currentRequestRate;
        this.ewmaRequestRate = ewmaRequestRate;
        this.errorRate = errorRate;
        this.totalRequests = totalRequests;
        this.allowedRequests = allowedRequests;
        this.rejectedRequests = rejectedRequests;
    }

    public static TrafficAssessment empty() {
        return new TrafficAssessment(null, null, null, null, null, null);
    }

    public Double getCurrentRequestRate() {
        return currentRequestRate;
    }

    public Double getEwmaRequestRate() {
        return ewmaRequestRate;
    }

    public Double getErrorRate() {
        return errorRate;
    }

    public Long getTotalRequests() {
        return totalRequests;
    }

    public Long getAllowedRequests() {
        return allowedRequests;
    }

    public Long getRejectedRequests() {
        return rejectedRequests;
    }
}
