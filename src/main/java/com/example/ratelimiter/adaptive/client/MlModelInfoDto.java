package com.example.ratelimiter.adaptive.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MlModelInfoDto {
    private String model;
    private double contamination;
    @JsonProperty("n_estimators")
    private int nEstimators;
    @JsonProperty("random_state")
    private int randomState;
    @JsonProperty("feature_names")
    private List<String> featureNames;
    @JsonProperty("observation_interval_seconds")
    private double observationIntervalSeconds;
    @JsonProperty("is_fitted")
    private boolean fitted;
    @JsonProperty("training_samples")
    private int trainingSamples;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getContamination() {
        return contamination;
    }

    public void setContamination(double contamination) {
        this.contamination = contamination;
    }

    public int getNEstimators() {
        return nEstimators;
    }

    public void setNEstimators(int nEstimators) {
        this.nEstimators = nEstimators;
    }

    public int getRandomState() {
        return randomState;
    }

    public void setRandomState(int randomState) {
        this.randomState = randomState;
    }

    public List<String> getFeatureNames() {
        return featureNames;
    }

    public void setFeatureNames(List<String> featureNames) {
        this.featureNames = featureNames;
    }

    public double getObservationIntervalSeconds() {
        return observationIntervalSeconds;
    }

    public void setObservationIntervalSeconds(double observationIntervalSeconds) {
        this.observationIntervalSeconds = observationIntervalSeconds;
    }

    public boolean isFitted() {
        return fitted;
    }

    public void setFitted(boolean fitted) {
        this.fitted = fitted;
    }

    public int getTrainingSamples() {
        return trainingSamples;
    }

    public void setTrainingSamples(int trainingSamples) {
        this.trainingSamples = trainingSamples;
    }
}
