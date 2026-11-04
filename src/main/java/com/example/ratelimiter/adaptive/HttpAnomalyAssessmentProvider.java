package com.example.ratelimiter.adaptive;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.ratelimiter.adaptive.client.AnomalyServiceClient;
import com.example.ratelimiter.adaptive.client.DetectResponseDto;
import com.example.ratelimiter.adaptive.client.TrafficFeaturesRequest;
import com.example.ratelimiter.adaptive.mapping.MetricsToFeaturesMapper;

/**
 * Anomaly assessment provider backed by the Python ML service.
 *
 * <p>All transport errors, timeouts, invalid responses, and interrupted calls
 * are converted to {@link AnomalyAssessment#unavailable()} so the decision
 * engine can apply its conservative fallback instead of failing an evaluation.
 */
@Component
public class HttpAnomalyAssessmentProvider implements AnomalyAssessmentProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpAnomalyAssessmentProvider.class);

    private final AnomalyServiceClient client;
    private final MetricsToFeaturesMapper mapper;

    public HttpAnomalyAssessmentProvider(AnomalyServiceClient client, MetricsToFeaturesMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public AnomalyAssessment assess(TrafficAssessment trafficAssessment) {
        try {
            TrafficFeaturesRequest request = mapper.toRequest(trafficAssessment);
            if (request == null) {
                return AnomalyAssessment.unavailable();
            }
            DetectResponseDto response = client.detect(request);
            if (response == null || response.getIsAnomaly() == null) {
                return AnomalyAssessment.unavailable();
            }
            return new AnomalyAssessment(response.getIsAnomaly(), response.getAnomalyScore(),
                    response.getPrediction(), response.getModel());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("ML anomaly assessment interrupted; using conservative fallback");
            return AnomalyAssessment.unavailable();
        } catch (IOException | RuntimeException ex) {
            log.warn("ML anomaly assessment unavailable ({}); using conservative fallback", ex.toString());
            return AnomalyAssessment.unavailable();
        }
    }
}
