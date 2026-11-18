package com.example.ratelimiter.adaptive.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.ratelimiter.adaptive.TrafficAssessment;
import com.example.ratelimiter.adaptive.client.TrafficFeaturesRequest;

class MetricsToFeaturesMapperTest {

    private final MetricsToFeaturesMapper mapper = new MetricsToFeaturesMapper();

    @Test
    void usesCompletedWindowCountsAndClientErrorRate() {
        TrafficAssessment traffic = new TrafficAssessment(
                20.0, 10.0, 0.4, 1000L, 700L, 300L, 25L, 0.08);

        TrafficFeaturesRequest request = mapper.toRequest(traffic);

        assertThat(request.getRequestCount()).isEqualTo(25L);
        assertThat(request.getErrorRate()).isEqualTo(0.08);
        assertThat(request.getRequestRate()).isEqualTo(20.0);
        assertThat(request.getBurstRatio()).isEqualTo(2.0);
    }

    @Test
    void derivesRequestRateFromTheSameWindowCountAndDuration() {
        TrafficAssessment traffic = new TrafficAssessment(
                99.0, 10.0, 0.4, 1000L, 700L, 300L, 25L, 0.08, 7L, 50.0);

        TrafficFeaturesRequest request = mapper.toRequest(traffic);

        // 25 requests over a 50 second window = 0.5 req/s. The stale 99.0 rate
        // supplied by the assessment must not leak into the feature vector.
        assertThat(request.getRequestRate()).isEqualTo(0.5);
        assertThat(request.getRequestCount()).isEqualTo(25L);
        assertThat(request.getErrorRate()).isEqualTo(0.08);
        assertThat(request.getBurstRatio()).isEqualTo(0.05);
    }

    @Test
    void matchesSharedCrossLanguageFeatureContract() {
        // Same fixture as SHARED_WINDOW_CONTRACT in ml-service/tests/conftest.py:
        // 25 requests over a 50s window, 5 errors (0.2), baseline 10 req/s.
        TrafficAssessment traffic = new TrafficAssessment(
                999.0, 10.0, 0.9, 999L, 900L, 99L, 25L, 0.2, 42L, 50.0);

        TrafficFeaturesRequest request = mapper.toRequest(traffic);

        assertThat(request.getRequestRate()).isEqualTo(0.5);
        assertThat(request.getErrorRate()).isEqualTo(0.2);
        assertThat(request.getBurstRatio()).isEqualTo(0.05);
        assertThat(request.getRequestCount()).isEqualTo(25L);
    }

    @Test
    void fallsBackToLifetimeTotalsWhenWindowSnapshotIsUnavailable() {
        TrafficAssessment traffic = new TrafficAssessment(
                10.0, 9.0, 0.01, 100L, 99L, 1L);

        TrafficFeaturesRequest request = mapper.toRequest(traffic);

        assertThat(request.getRequestCount()).isEqualTo(100L);
        assertThat(request.getErrorRate()).isEqualTo(0.01);
    }

    @Test
    void zeroBaselineBurstRatioMatchesPythonFeatureContract() {
        TrafficAssessment traffic = new TrafficAssessment(
                25.0, 0.0, 0.0, 500L, 500L, 0L, 50L, 0.0, 3L, 2.0);

        TrafficFeaturesRequest request = mapper.toRequest(traffic);

        assertThat(request.getRequestRate()).isEqualTo(25.0);
        assertThat(request.getBurstRatio())
                .isEqualTo(MetricsToFeaturesMapper.ZERO_BASELINE_BURST_RATIO);
    }

    @Test
    void idleWindowProducesZeroFeaturesFromTheSameWindow() {
        TrafficAssessment traffic = new TrafficAssessment(
                99.0, 5.0, 0.4, 1000L, 700L, 300L, 0L, 0.0, 9L, 30.0);

        TrafficFeaturesRequest request = mapper.toRequest(traffic);

        assertThat(request.getRequestCount()).isZero();
        assertThat(request.getRequestRate()).isZero();
        assertThat(request.getErrorRate()).isZero();
        assertThat(request.getBurstRatio()).isZero();
    }

    @Test
    void windowCountWithoutDurationUsesTheSameCompletedWindowRate() {
        TrafficAssessment traffic = new TrafficAssessment(
                2.5, 2.0, 0.4, 1000L, 700L, 300L, 50L, 0.1);

        TrafficFeaturesRequest request = mapper.toRequest(traffic);

        // The supplied rate and window count both describe the last completed
        // window, so no lifetime total leaks into the feature vector.
        assertThat(request.getRequestCount()).isEqualTo(50L);
        assertThat(request.getRequestRate()).isEqualTo(2.5);
        assertThat(request.getErrorRate()).isEqualTo(0.1);
        assertThat(request.getBurstRatio()).isEqualTo(1.25);
    }

    @Test
    void nullAssessmentReturnsNull() {
        assertThat(mapper.toRequest(null)).isNull();
    }
}
