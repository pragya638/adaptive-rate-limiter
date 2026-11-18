package com.example.ratelimiter.adaptive.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MlHealthDto {
    private String status;
    @JsonProperty("model_ready")
    private boolean modelReady;
    private String model;
    private String version;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isModelReady() {
        return modelReady;
    }

    public void setModelReady(boolean modelReady) {
        this.modelReady = modelReady;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
