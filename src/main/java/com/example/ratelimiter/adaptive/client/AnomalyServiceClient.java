package com.example.ratelimiter.adaptive.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.example.ratelimiter.adaptive.MlServiceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AnomalyServiceClient {
    private final MlServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AnomalyServiceClient(MlServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    public DetectResponseDto detect(TrafficFeaturesRequest request) throws IOException, InterruptedException {
        if (!properties.isEnabled()) {
            throw new IOException("ML service disabled");
        }
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        String body = objectMapper.writeValueAsString(request);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl().replaceAll("/+$", "") + "/detect"))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("ML service returned status: " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), DetectResponseDto.class);
    }
}
