package com.example.ratelimiter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ratelimiter.monitoring.TrafficMetrics;

@SpringBootTest
@AutoConfigureMockMvc
class MetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrafficMetrics metrics;

    @Test
    void healthAndMetricsDoNotPolluteCounters() throws Exception {
        long totalBefore = metrics.getTotalRequests();
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/metrics")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").exists())
                .andExpect(jsonPath("$.currentRequestRate").exists())
                .andExpect(jsonPath("$.ewmaRequestRate").exists());
        assertThat(metrics.getTotalRequests()).isEqualTo(totalBefore);
    }

    @Test
    void demoRequestsUpdateCounters() throws Exception {
        long totalBefore = metrics.getTotalRequests();
        long allowedBefore = metrics.getAllowedRequests();
        mockMvc.perform(get("/api/demo").header("X-Client-Id", "test-user")).andExpect(status().isOk());
        assertThat(metrics.getTotalRequests()).isGreaterThan(totalBefore);
        assertThat(metrics.getAllowedRequests()).isGreaterThan(allowedBefore);
    }
}
