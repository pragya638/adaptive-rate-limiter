package com.example.ratelimiter.adaptive.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.ratelimiter.adaptive.MlServiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Wire-level checks for the real HTTP client used to call the Python ML
 * service. A local JDK HTTP server stands in for FastAPI so the JSON field
 * names and response parsing are exercised without mocking the client.
 */
class AnomalyServiceClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("POST /detect sends the documented feature names and parses the ML response")
    void sendsDocumentedFeaturesAndParsesResponse() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedMethod = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            receivedMethod.set(exchange.getRequestMethod());
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"is_anomaly\":true,\"prediction\":\"anomaly\","
                    + "\"anomaly_score\":0.73,\"model\":\"IsolationForest\"}");
        });

        try {
            AnomalyServiceClient client = new AnomalyServiceClient(
                    propertiesFor(server, true), objectMapper);

            DetectResponseDto response = client.detect(new TrafficFeaturesRequest(2.5, 0.1, 1.25, 50));

            assertThat(receivedMethod.get()).isEqualTo("POST");
            JsonNode json = objectMapper.readTree(receivedBody.get());
            assertThat(json.get("request_rate").asDouble()).isEqualTo(2.5);
            assertThat(json.get("error_rate").asDouble()).isEqualTo(0.1);
            assertThat(json.get("burst_ratio").asDouble()).isEqualTo(1.25);
            assertThat(json.get("request_count").asLong()).isEqualTo(50L);

            assertThat(response.getIsAnomaly()).isTrue();
            assertThat(response.getPrediction()).isEqualTo("anomaly");
            assertThat(response.getAnomalyScore()).isEqualTo(0.73);
            assertThat(response.getModel()).isEqualTo("IsolationForest");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("non-2xx ML responses fail with IOException")
    void nonSuccessStatusFails() throws Exception {
        HttpServer server = startServer(exchange -> respond(exchange, 503, "{\"detail\":\"not ready\"}"));

        try {
            AnomalyServiceClient client = new AnomalyServiceClient(
                    propertiesFor(server, true), objectMapper);

            assertThatThrownBy(() -> client.detect(new TrafficFeaturesRequest(1.0, 0.0, 1.0, 1)))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("503");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("GET /health and GET /model/info parse the documented system responses")
    void readsHealthAndModelInfo() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            try {
                respond(exchange, 200,
                        "{\"status\":\"ok\",\"model_ready\":true,\"model\":\"IsolationForest\",\"version\":\"0.4.0\"}");
            } finally {
                exchange.close();
            }
        });
        server.createContext("/model/info", exchange -> {
            try {
                respond(exchange, 200,
                        "{\"model\":\"IsolationForest\",\"contamination\":0.05,\"n_estimators\":200,"
                                + "\"random_state\":42,\"feature_names\":[\"request_rate\",\"error_rate\","
                                + "\"burst_ratio\",\"request_count\"],\"observation_interval_seconds\":30.0,"
                                + "\"is_fitted\":true,\"training_samples\":2000}");
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            AnomalyServiceClient client = new AnomalyServiceClient(
                    propertiesFor(server, true), objectMapper);

            MlHealthDto health = client.health();
            MlModelInfoDto modelInfo = client.modelInfo();

            assertThat(health.getStatus()).isEqualTo("ok");
            assertThat(health.isModelReady()).isTrue();
            assertThat(health.getModel()).isEqualTo("IsolationForest");
            assertThat(health.getVersion()).isEqualTo("0.4.0");
            assertThat(modelInfo.getModel()).isEqualTo("IsolationForest");
            assertThat(modelInfo.getContamination()).isEqualTo(0.05);
            assertThat(modelInfo.getNEstimators()).isEqualTo(200);
            assertThat(modelInfo.getRandomState()).isEqualTo(42);
            assertThat(modelInfo.getFeatureNames()).containsExactly(
                    "request_rate", "error_rate", "burst_ratio", "request_count");
            assertThat(modelInfo.getObservationIntervalSeconds()).isEqualTo(30.0);
            assertThat(modelInfo.isFitted()).isTrue();
            assertThat(modelInfo.getTrainingSamples()).isEqualTo(2000);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("a disabled ML client fails without sending a system request")
    void disabledClientFailsFast() {
        MlServiceProperties properties = new MlServiceProperties();
        properties.setBaseUrl("http://127.0.0.1:1");
        properties.setEnabled(false);

        AnomalyServiceClient client = new AnomalyServiceClient(properties, objectMapper);

        assertThatThrownBy(() -> client.health())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("disabled");
    }

    private HttpServer startServer(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/detect", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private MlServiceProperties propertiesFor(HttpServer server, boolean enabled) {
        MlServiceProperties properties = new MlServiceProperties();
        // Include a trailing slash to verify the client normalises the base URL.
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        properties.setConnectTimeoutMs(3000);
        properties.setReadTimeoutMs(3000);
        properties.setEnabled(enabled);
        return properties;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        // Drain the request body so the exchange can complete even when the
        // handler does not inspect it.
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
