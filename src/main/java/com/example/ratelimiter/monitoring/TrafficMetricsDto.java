package com.example.ratelimiter.monitoring;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TrafficMetricsDto {
    @JsonProperty("totalRequests")
    private long totalRequests;

    @JsonProperty("allowedRequests")
    private long allowedRequests;

    @JsonProperty("rejectedRequests")
    private long rejectedRequests;

    @JsonProperty("errorRate")
    private double errorRate;

    @JsonProperty("currentRequestRate")
    private double currentRequestRate;

    @JsonProperty("ewmaRequestRate")
    private double ewmaRequestRate;

    @JsonProperty("observationTimestamp")
    private Instant observationTimestamp;

    public TrafficMetricsDto() {
    }

    public TrafficMetricsDto(long totalRequests, long allowedRequests, long rejectedRequests,
            double errorRate, double currentRequestRate, double ewmaRequestRate,
            Instant observationTimestamp) {
        this.totalRequests = totalRequests;
        this.allowedRequests = allowedRequests;
        this.rejectedRequests = rejectedRequests;
        this.errorRate = errorRate;
        this.currentRequestRate = currentRequestRate;
        this.ewmaRequestRate = ewmaRequestRate;
        this.observationTimestamp = observationTimestamp;
    }

    public long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }

    public long getAllowedRequests() { return allowedRequests; }
    public void setAllowedRequests(long allowedRequests) { this.allowedRequests = allowedRequests; }

    public long getRejectedRequests() { return rejectedRequests; }
    public void setRejectedRequests(long rejectedRequests) { this.rejectedRequests = rejectedRequests; }

    public double getErrorRate() { return errorRate; }
    public void setErrorRate(double errorRate) { this.errorRate = errorRate; }

    public double getCurrentRequestRate() { return currentRequestRate; }
    public void setCurrentRequestRate(double currentRequestRate) { this.currentRequestRate = currentRequestRate; }

    public double getEwmaRequestRate() { return ewmaRequestRate; }
    public void setEwmaRequestRate(double ewmaRequestRate) { this.ewmaRequestRate = ewmaRequestRate; }

    public Instant getObservationTimestamp() { return observationTimestamp; }
    public void setObservationTimestamp(Instant observationTimestamp) { this.observationTimestamp = observationTimestamp; }
}
