package com.example.ratelimiter.adaptive;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.example.ratelimiter.adaptive.client.TrafficFeaturesRequest;
import com.example.ratelimiter.adaptive.mapping.MetricsToFeaturesMapper;
import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.monitoring.MonitoringService;
import com.example.ratelimiter.monitoring.TrafficMetricsDto;

@Service
public class AdaptiveStatusService {

    private final RateLimitProperties rateLimitProperties;
    private final AdaptiveEngineProperties engineProperties;
    private final MonitoringService monitoringService;
    private final ObjectProvider<AdaptiveLimitAdvisor> advisorProvider;
    private final MetricsToFeaturesMapper featuresMapper;

    public AdaptiveStatusService(RateLimitProperties rateLimitProperties,
            AdaptiveEngineProperties engineProperties,
            MonitoringService monitoringService,
            ObjectProvider<AdaptiveLimitAdvisor> advisorProvider,
            MetricsToFeaturesMapper featuresMapper) {
        this.rateLimitProperties = rateLimitProperties;
        this.engineProperties = engineProperties;
        this.monitoringService = monitoringService;
        this.advisorProvider = advisorProvider;
        this.featuresMapper = featuresMapper;
    }

    public AdaptiveStatusDto snapshot() {
        AdaptiveLimitAdvisor advisor = advisorProvider.getIfAvailable();
        boolean enabled = advisor != null;
        TrafficMetricsDto metrics = monitoringService.getMetrics();

        int currentLimit = enabled ? advisor.getCurrentLimit() : rateLimitProperties.getMaxRequests();

        return new AdaptiveStatusDto(
                enabled,
                enabled ? "ADAPTIVE" : "STATIC",
                currentLimit,
                engineProperties.getMinLimit(),
                engineProperties.getMaxLimit(),
                engineProperties.getInitialLimit(),
                enabled ? advisor.getLastEvaluationAt() : null,
                enabled ? advisor.getLastAdaptationAt() : null,
                anomalyStatus(enabled ? advisor.getLastAnomalyAssessment() : null),
                featureVector(enabled ? advisor.getLastTrafficAssessment() : null),
                trafficWindow(metrics),
                decisionStatus(enabled ? advisor.getLastDecision() : null));
    }

    private AdaptiveStatusDto.AnomalyStatus anomalyStatus(AnomalyAssessment assessment) {
        if (assessment == null) {
            return null;
        }
        return new AdaptiveStatusDto.AnomalyStatus(
                assessment.isAvailable(),
                assessment.getIsAnomaly(),
                assessment.getAnomalyScore(),
                assessment.getPrediction(),
                assessment.getModel());
    }

    private AdaptiveStatusDto.FeatureVectorStatus featureVector(TrafficAssessment traffic) {
        TrafficFeaturesRequest request = featuresMapper.toRequest(traffic);
        if (request == null) {
            return null;
        }
        return new AdaptiveStatusDto.FeatureVectorStatus(
                request.getRequestRate(),
                request.getErrorRate(),
                request.getBurstRatio(),
                request.getRequestCount());
    }

    private AdaptiveStatusDto.TrafficWindowStatus trafficWindow(TrafficMetricsDto metrics) {
        return new AdaptiveStatusDto.TrafficWindowStatus(
                metrics.getWindowSequence(),
                metrics.getWindowStart(),
                metrics.getWindowEnd(),
                metrics.getWindowDurationSeconds(),
                metrics.getWindowRequestCount(),
                metrics.getWindowAllowedRequests(),
                metrics.getWindowRejectedRequests(),
                metrics.getClientErrorRate(),
                metrics.getCurrentRequestRate(),
                metrics.getEwmaRequestRate());
    }

    private AdaptiveStatusDto.DecisionStatus decisionStatus(AdaptiveDecision decision) {
        if (decision == null) {
            return null;
        }
        return new AdaptiveStatusDto.DecisionStatus(
                decision.getAction() == null ? null : decision.getAction().name(),
                decision.getCurrentLimit(),
                decision.getProposedLimit(),
                decision.getReasonCode(),
                decision.getExplanation(),
                decision.getEvaluatedAt());
    }
}
