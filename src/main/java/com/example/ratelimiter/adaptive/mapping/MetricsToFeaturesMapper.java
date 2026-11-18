package com.example.ratelimiter.adaptive.mapping;

import org.springframework.stereotype.Component;

import com.example.ratelimiter.adaptive.TrafficAssessment;
import com.example.ratelimiter.adaptive.client.TrafficFeaturesRequest;

@Component
public class MetricsToFeaturesMapper {

    /**
     * Value used for {@code burst_ratio} when the baseline is zero but traffic
     * exists. This mirrors {@code ZERO_BASELINE_BURST_RATIO} in the Python
     * detector so the inference contract matches the documented training
     * contract instead of leaking an unbounded ratio into the model.
     */
    public static final double ZERO_BASELINE_BURST_RATIO = 10.0;

    public TrafficFeaturesRequest toRequest(TrafficAssessment traffic) {
        if (traffic == null) {
            return null;
        }
        double requestRate = traffic.getCurrentRequestRate() == null ? 0.0 : traffic.getCurrentRequestRate();
        double ewma = traffic.getEwmaRequestRate() == null ? 0.0 : traffic.getEwmaRequestRate();
        double errorRate = traffic.getEffectiveErrorRate() == null ? 0.0 : traffic.getEffectiveErrorRate();
        long requestCount = traffic.getEffectiveRequestCount() == null ? 0L : traffic.getEffectiveRequestCount();

        // The feature contract defines request_rate as request_count divided by
        // the observation interval. When the completed-window metadata is
        // available, derive the rate from that same window so training and
        // inference cannot disagree about the time window.
        Double windowDurationSeconds = traffic.getWindowDurationSeconds();
        if (traffic.getWindowRequestCount() != null
                && windowDurationSeconds != null && windowDurationSeconds > 0.0
                && !Double.isNaN(windowDurationSeconds) && !Double.isInfinite(windowDurationSeconds)) {
            requestRate = requestCount / windowDurationSeconds;
        }
        if (Double.isNaN(requestRate) || Double.isInfinite(requestRate) || requestRate < 0.0) {
            requestRate = 0.0;
        }
        if (Double.isNaN(ewma) || Double.isInfinite(ewma) || ewma < 0.0) {
            ewma = 0.0;
        }
        if (Double.isNaN(errorRate) || Double.isInfinite(errorRate) || errorRate < 0.0) {
            errorRate = 0.0;
        }
        if (errorRate > 1.0) {
            errorRate = 1.0;
        }
        if (requestCount < 0) {
            requestCount = 0L;
        }
        double burstRatio;
        if (ewma > 0.0) {
            burstRatio = requestRate / ewma;
        } else if (requestRate > 0.0) {
            // Match the Python detector's documented zero-baseline rule.
            burstRatio = ZERO_BASELINE_BURST_RATIO;
        } else {
            burstRatio = 0.0;
        }
        if (Double.isNaN(burstRatio) || Double.isInfinite(burstRatio) || burstRatio < 0.0) {
            burstRatio = 0.0;
        }
        return new TrafficFeaturesRequest(requestRate, errorRate, burstRatio, requestCount);
    }
}
