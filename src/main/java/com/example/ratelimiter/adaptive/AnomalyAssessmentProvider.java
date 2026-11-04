package com.example.ratelimiter.adaptive;

public interface AnomalyAssessmentProvider {
    AnomalyAssessment assess(TrafficAssessment trafficAssessment);
}
