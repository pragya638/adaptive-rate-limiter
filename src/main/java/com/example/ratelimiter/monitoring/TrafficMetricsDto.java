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

    @JsonProperty("windowRequestCount")
    private long windowRequestCount;

    @JsonProperty("windowAllowedRequests")
    private long windowAllowedRequests;

    @JsonProperty("windowRejectedRequests")
    private long windowRejectedRequests;

    @JsonProperty("errorRate")
    private double errorRate;

    @JsonProperty("clientErrorRate")
    private double clientErrorRate;

    @JsonProperty("currentRequestRate")
    private double currentRequestRate;

    @JsonProperty("ewmaRequestRate")
    private double ewmaRequestRate;

    @JsonProperty("observationTimestamp")
    private Instant observationTimestamp;

    @JsonProperty("windowSequence")
    private long windowSequence;

    @JsonProperty("windowStart")
    private Instant windowStart;

    @JsonProperty("windowEnd")
    private Instant windowEnd;

    @JsonProperty("windowDurationSeconds")
    private double windowDurationSeconds;

    public TrafficMetricsDto() {
    }

    public TrafficMetricsDto(long totalRequests, long allowedRequests, long rejectedRequests,
            double errorRate, double currentRequestRate, double ewmaRequestRate,
            Instant observationTimestamp) {
        this(totalRequests, allowedRequests, rejectedRequests, errorRate, currentRequestRate,
                ewmaRequestRate, observationTimestamp, totalRequests, allowedRequests, rejectedRequests, errorRate);
    }

    public TrafficMetricsDto(long totalRequests, long allowedRequests, long rejectedRequests,
            double errorRate, double currentRequestRate, double ewmaRequestRate,
            Instant observationTimestamp, long windowRequestCount, long windowAllowedRequests,
            long windowRejectedRequests, double clientErrorRate) {
        this(totalRequests, allowedRequests, rejectedRequests, errorRate, currentRequestRate,
                ewmaRequestRate, observationTimestamp, windowRequestCount, windowAllowedRequests,
                windowRejectedRequests, clientErrorRate, 0L, null, null, 0.0);
    }

    public TrafficMetricsDto(long totalRequests, long allowedRequests, long rejectedRequests,
            double errorRate, double currentRequestRate, double ewmaRequestRate,
            Instant observationTimestamp, long windowRequestCount, long windowAllowedRequests,
            long windowRejectedRequests, double clientErrorRate, long windowSequence,
            Instant windowStart, Instant windowEnd, double windowDurationSeconds) {
        this.totalRequests = totalRequests;
        this.allowedRequests = allowedRequests;
        this.rejectedRequests = rejectedRequests;
        this.windowRequestCount = windowRequestCount;
        this.windowAllowedRequests = windowAllowedRequests;
        this.windowRejectedRequests = windowRejectedRequests;
        this.errorRate = errorRate;
        this.clientErrorRate = clientErrorRate;
        this.currentRequestRate = currentRequestRate;
        this.ewmaRequestRate = ewmaRequestRate;
        this.observationTimestamp = observationTimestamp;
        this.windowSequence = windowSequence;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.windowDurationSeconds = windowDurationSeconds;
    }

    public long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }

    public long getAllowedRequests() { return allowedRequests; }
    public void setAllowedRequests(long allowedRequests) { this.allowedRequests = allowedRequests; }

    public long getRejectedRequests() { return rejectedRequests; }
    public void setRejectedRequests(long rejectedRequests) { this.rejectedRequests = rejectedRequests; }

    public long getWindowRequestCount() { return windowRequestCount; }
    public void setWindowRequestCount(long windowRequestCount) { this.windowRequestCount = windowRequestCount; }

    public long getWindowAllowedRequests() { return windowAllowedRequests; }
    public void setWindowAllowedRequests(long windowAllowedRequests) { this.windowAllowedRequests = windowAllowedRequests; }

    public long getWindowRejectedRequests() { return windowRejectedRequests; }
    public void setWindowRejectedRequests(long windowRejectedRequests) { this.windowRejectedRequests = windowRejectedRequests; }

    public double getErrorRate() { return errorRate; }
    public void setErrorRate(double errorRate) { this.errorRate = errorRate; }

    public double getClientErrorRate() { return clientErrorRate; }
    public void setClientErrorRate(double clientErrorRate) { this.clientErrorRate = clientErrorRate; }

    public double getCurrentRequestRate() { return currentRequestRate; }
    public void setCurrentRequestRate(double currentRequestRate) { this.currentRequestRate = currentRequestRate; }

    public double getEwmaRequestRate() { return ewmaRequestRate; }
    public void setEwmaRequestRate(double ewmaRequestRate) { this.ewmaRequestRate = ewmaRequestRate; }

    public Instant getObservationTimestamp() { return observationTimestamp; }
    public void setObservationTimestamp(Instant observationTimestamp) { this.observationTimestamp = observationTimestamp; }

    public long getWindowSequence() { return windowSequence; }
    public void setWindowSequence(long windowSequence) { this.windowSequence = windowSequence; }

    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }

    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }

    public double getWindowDurationSeconds() { return windowDurationSeconds; }
    public void setWindowDurationSeconds(double windowDurationSeconds) { this.windowDurationSeconds = windowDurationSeconds; }
}
