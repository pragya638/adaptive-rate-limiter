package com.example.ratelimiter.adaptive.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DetectResponseDto {
    @JsonProperty("is_anomaly")
    private Boolean isAnomaly;
    private String prediction;
    @JsonProperty("anomaly_score")
    private Double anomalyScore;
    private String model;

    public DetectResponseDto() {
    }

    public Boolean getIsAnomaly() {
        return isAnomaly;
    }

    public void setIsAnomaly(Boolean isAnomaly) {
        this.isAnomaly = isAnomaly;
    }

    public String getPrediction() {
        return prediction;
    }

    public void setPrediction(String prediction) {
        this.prediction = prediction;
    }

    public Double getAnomalyScore() {
        return anomalyScore;
    }

    public void setAnomalyScore(Double anomalyScore) {
        this.anomalyScore = anomalyScore;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
