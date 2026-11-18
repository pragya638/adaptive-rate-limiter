package com.example.ratelimiter.adaptive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.ratelimiter.adaptive.client.AnomalyServiceClient;
import com.example.ratelimiter.adaptive.integration.AdaptiveEvaluationScheduler;
import com.example.ratelimiter.adaptive.mapping.MetricsToFeaturesMapper;
import com.example.ratelimiter.observability.MlEvaluationTracker;
import com.example.ratelimiter.monitoring.CompletedWindowStore;
import com.example.ratelimiter.monitoring.TrafficMetricsDto;
import com.example.ratelimiter.ratelimit.DynamicRateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Exercises the complete adaptive loop with the real HTTP client:
 * monitoring snapshot -> scheduler -> ML HTTP call (local stand-in for the
 * Python service) -> provider -> decision engine -> advisor -> dynamic limiter.
 *
 * <p>The stub speaks the exact FastAPI/Machine-Learning JSON contract. This
 * verifies the wire format and the limiter application without requiring a
 * running Python process in the unit-test phase. A live Python service is
 * verified separately in the Phase 5 integration report.
 */
class AdaptiveMlIntegrationTest {

    private static final String CLIENT = "integration-client";
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicReference<JsonNode> lastRequest = new AtomicReference<>();
    private final AtomicLong sequence = new AtomicLong();

    private HttpServer server;
    private TestClock clock;
    private DynamicRateLimiter limiter;
    private AdaptiveLimitAdvisor advisor;
    private CompletedWindowStore completedWindowStore;
    private AdaptiveEvaluationScheduler scheduler;

    private long windowCount = 25L;
    private double windowDurationSeconds = 50.0;
    private double clientErrorRate = 0.2;
    private double ewmaRequestRate = 10.0;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/detect", this::handleDetect);
        server.start();

        AdaptiveEngineProperties engineProperties = new AdaptiveEngineProperties();
        engineProperties.setMinLimit(1);
        engineProperties.setMaxLimit(50);
        engineProperties.setInitialLimit(5);
        engineProperties.setIncreaseStep(5);
        engineProperties.setDecreaseStep(10);
        engineProperties.setDecreaseFraction(0.2);
        engineProperties.setRecoveryStep(1);
        engineProperties.setRecoveryWindows(2);
        engineProperties.setHighErrorRateThreshold(0.1);
        engineProperties.setAnomalyConfirmationWindows(1);
        engineProperties.setGrowthConfirmationWindows(1);

        AdaptiveIntegrationProperties integrationProperties = new AdaptiveIntegrationProperties();
        integrationProperties.setCooldownSeconds(0);

        clock = new TestClock(Instant.parse("2026-11-04T00:00:00Z"));
        limiter = new DynamicRateLimiter(5, 1, 50, RATE_LIMIT_WINDOW, clock);
        AdaptiveDecisionEngine engine = new AdaptiveDecisionEngine(engineProperties, clock);
        advisor = new AdaptiveLimitAdvisor(engine, limiter, integrationProperties, clock);

        MlServiceProperties mlProperties = new MlServiceProperties();
        mlProperties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        mlProperties.setConnectTimeoutMs(3000);
        mlProperties.setReadTimeoutMs(3000);
        mlProperties.setEnabled(true);

        AnomalyServiceClient client = new AnomalyServiceClient(mlProperties, objectMapper);
        AnomalyAssessmentProvider provider = new HttpAnomalyAssessmentProvider(
                client, new MetricsToFeaturesMapper(), new MlEvaluationTracker(clock));

        completedWindowStore = new CompletedWindowStore();
        scheduler = new AdaptiveEvaluationScheduler(completedWindowStore, provider, advisor);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("an anomalous ML response reduces the limit gradually and the limiter enforces it")
    void anomalousResponseUpdatesLimiterAndWireContractIsCorrect() {
        responseBody.set(anomalyResponse());

        completedWindowStore.publish(currentMetrics());
        scheduler.evaluate();

        assertThat(limiter.getCurrentLimit()).isEqualTo(4);

        JsonNode sent = lastRequest.get();
        assertThat(sent.get("request_count").asLong()).isEqualTo(25L);
        assertThat(sent.get("request_rate").asDouble()).isEqualTo(0.5);
        assertThat(sent.get("error_rate").asDouble()).isEqualTo(0.2);
        assertThat(sent.get("burst_ratio").asDouble()).isEqualTo(0.05);

        for (int i = 0; i < 4; i++) {
            assertThat(limiter.tryAcquire(CLIENT).allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire(CLIENT).allowed()).isFalse();
    }

    @Test
    @DisplayName("repeated anomalies walk the limit to the minimum, never collapsing in one step")
    void repeatedAnomaliesReachMinimumGradually() {
        responseBody.set(anomalyResponse());

        assertThat(applyNext().getProposedLimit()).isEqualTo(4);
        assertThat(applyNext().getProposedLimit()).isEqualTo(3);
        assertThat(applyNext().getProposedLimit()).isEqualTo(2);
        assertThat(applyNext().getProposedLimit()).isEqualTo(1);
        AdaptiveDecision atMinimum = applyNext();
        assertThat(atMinimum.getAction()).isEqualTo(DecisionAction.KEEP_LIMIT);
        assertThat(atMinimum.getReasonCode()).isEqualTo("AT_MIN_LIMIT");
        assertThat(limiter.getCurrentLimit()).isEqualTo(1);
    }

    @Test
    @DisplayName("normal ML responses recover the limit gradually")
    void normalResponsesRecoverLimit() {
        responseBody.set(anomalyResponse());
        configureNormalWindow();
        completedWindowStore.publish(currentMetrics());
        scheduler.evaluate();
        assertThat(limiter.getCurrentLimit()).isEqualTo(4);

        responseBody.set(normalResponse());
        AdaptiveDecision firstNormal = applyNext();
        assertThat(firstNormal.getAction()).isEqualTo(DecisionAction.KEEP_LIMIT);
        assertThat(firstNormal.getProposedLimit()).isEqualTo(4);

        AdaptiveDecision secondNormal = applyNext();
        assertThat(secondNormal.getAction()).isEqualTo(DecisionAction.INCREASE_LIMIT);
        assertThat(secondNormal.getReasonCode()).isEqualTo("RECOVERY");
        assertThat(limiter.getCurrentLimit()).isEqualTo(5);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(CLIENT).allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire(CLIENT).allowed()).isFalse();
    }

    private AdaptiveDecision applyNext() {
        completedWindowStore.publish(currentMetrics());
        scheduler.evaluate();
        return advisor.getLastDecision();
    }

    private void configureNormalWindow() {
        windowCount = 25L;
        windowDurationSeconds = 50.0;
        clientErrorRate = 0.02;
        ewmaRequestRate = 0.5;
    }

    private TrafficMetricsDto currentMetrics() {
        long currentSequence = sequence.incrementAndGet();
        Instant start = Instant.parse("2026-11-04T00:00:00Z");
        double currentRate = windowDurationSeconds > 0.0
                ? windowCount / windowDurationSeconds
                : 0.0;
        return new TrafficMetricsDto(
                1000L, 900L, 99L, 0.01, currentRate, ewmaRequestRate, start,
                windowCount, 20L, 5L, clientErrorRate, currentSequence,
                start, start.plusSeconds((long) windowDurationSeconds), windowDurationSeconds);
    }

    private void handleDetect(HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastRequest.set(objectMapper.readTree(body));
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } finally {
            exchange.close();
        }
    }

    private String normalResponse() {
        return "{\"is_anomaly\":false,\"prediction\":\"normal\","
                + "\"anomaly_score\":0.11,\"model\":\"IsolationForest\"}";
    }

    private String anomalyResponse() {
        return "{\"is_anomaly\":true,\"prediction\":\"anomaly\","
                + "\"anomaly_score\":0.81,\"model\":\"IsolationForest\"}";
    }

    private static final class TestClock extends Clock {

        private final Instant instant;

        private TestClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public long millis() {
            return instant.toEpochMilli();
        }
    }
}
