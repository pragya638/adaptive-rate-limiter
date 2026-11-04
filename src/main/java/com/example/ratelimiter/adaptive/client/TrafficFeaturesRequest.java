package com.example.ratelimiter.adaptive.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TrafficFeaturesRequest {
    @JsonProperty("request_rate")
    private double requestRate;
    @JsonProperty("error_rate")
    private double errorRate;
    @JsonProperty("burst_ratio")
    private double burstRatio;
    @JsonProperty("request_count")
    private long requestCount;

    public TrafficFeaturesRequest() {
    }

    public TrafficFeaturesRequest(double requestRate, double errorRate, double burstRatio, long requestCount) {
        this.requestRate = requestRate;
        this.errorRate = errorRate;
        this.burstRatio = burstRatio;
        this.requestCount = requestCount;
    }

    public double getRequestRate() {
        return requestRate;
    }

    public void setRequestRate(double requestRate) {
        this.requestRate = requestRate;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public double getBurstRatio() {
        return burstRatio;
    }

    public void setBurstRatio(double burstRatio) {
        this.burstRatio = burstRatio;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(long requestCount) {
        this.requestCount = requestCount;
    }
}
