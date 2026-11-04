package com.example.ratelimiter.adaptive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.ratelimiter.adaptive.client.AnomalyServiceClient;
import com.example.ratelimiter.adaptive.client.DetectResponseDto;
import com.example.ratelimiter.adaptive.client.TrafficFeaturesRequest;
import com.example.ratelimiter.adaptive.mapping.MetricsToFeaturesMapper;

@ExtendWith(MockitoExtension.class)
class HttpAnomalyAssessmentProviderTest {

    @Mock
    private AnomalyServiceClient client;

    private HttpAnomalyAssessmentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new HttpAnomalyAssessmentProvider(client, new MetricsToFeaturesMapper());
    }

    @Test
    @DisplayName("successful ML responses are mapped to an available assessment")
    void mapsSuccessfulResponse() throws Exception {
        DetectResponseDto response = new DetectResponseDto();
        response.setIsAnomaly(true);
        response.setAnomalyScore(0.42);
        response.setPrediction("anomaly");
        response.setModel("IsolationForest");
        when(client.detect(any(TrafficFeaturesRequest.class))).thenReturn(response);

        AnomalyAssessment assessment = provider.assess(traffic());

        assertThat(assessment.isAvailable()).isTrue();
        assertThat(assessment.isAnomalous()).isTrue();
        assertThat(assessment.getAnomalyScore()).isEqualTo(0.42);
        assertThat(assessment.getPrediction()).isEqualTo("anomaly");
        assertThat(assessment.getModel()).isEqualTo("IsolationForest");
        verify(client).detect(any(TrafficFeaturesRequest.class));
    }

    @Test
    @DisplayName("transport failures return an unavailable assessment")
    void transportFailureReturnsUnavailable() throws Exception {
        when(client.detect(any(TrafficFeaturesRequest.class))).thenThrow(new IOException("connection refused"));

        AnomalyAssessment assessment = provider.assess(traffic());

        assertThat(assessment.isAvailable()).isFalse();
        assertThat(assessment.isAnomalous()).isFalse();
    }

    @Test
    @DisplayName("invalid ML responses return an unavailable assessment")
    void invalidResponseReturnsUnavailable() throws Exception {
        DetectResponseDto response = new DetectResponseDto();
        when(client.detect(any(TrafficFeaturesRequest.class))).thenReturn(response);

        assertThat(provider.assess(traffic()).isAvailable()).isFalse();
    }

    @Test
    @DisplayName("interrupted calls preserve the interrupt flag and return unavailable")
    void interruptedCallReturnsUnavailable() throws Exception {
        when(client.detect(any(TrafficFeaturesRequest.class))).thenThrow(new InterruptedException("interrupted"));

        assertThatCode(() -> {
            AnomalyAssessment assessment = provider.assess(traffic());
            assertThat(assessment.isAvailable()).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            Thread.interrupted();
        }).doesNotThrowAnyException();
    }

    private TrafficAssessment traffic() {
        return new TrafficAssessment(10.0, 9.0, 0.01, 100L, 99L, 1L);
    }
}
