package com.example.ratelimiter.adaptive;

public class AnomalyAssessment {
    private final Boolean isAnomaly;
    private final Double anomalyScore;
    private final String prediction;
    private final String model;

    public AnomalyAssessment(Boolean isAnomaly, Double anomalyScore, String prediction, String model) {
        this.isAnomaly = isAnomaly;
        this.anomalyScore = anomalyScore;
        this.prediction = prediction;
        this.model = model;
    }

    public static AnomalyAssessment unavailable() {
        return new AnomalyAssessment(null, null, null, null);
    }

    public Boolean getIsAnomaly() {
        return isAnomaly;
    }

    public Double getAnomalyScore() {
        return anomalyScore;
    }

    public String getPrediction() {
        return prediction;
    }

    public String getModel() {
        return model;
    }

    public boolean isAvailable() {
        return isAnomaly != null;
    }

    public boolean isAnomalous() {
        return Boolean.TRUE.equals(isAnomaly);
    }
}
