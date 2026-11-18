package com.example.ratelimiter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ratelimiter.monitoring.TrafficMetrics;

@SpringBootTest(properties = {
        "adaptive.enabled=false",
        "rate-limit.max-requests=1",
        "rate-limit.window=60s",
        "ml-service.base-url=http://127.0.0.1:1",
        "ml-service.connect-timeout-ms=50",
        "ml-service.read-timeout-ms=50",
        "monitoring.observation-interval=1h"
})
@AutoConfigureMockMvc
class SystemStatusIntegrationTest {

    private static final String SYSTEM_STATUS = "/api/system-status";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrafficMetrics metrics;

    @Test
    @DisplayName("system status exposes verified configuration and unavailable dependency state")
    void systemStatusExposesReadOnlyState() throws Exception {
        mockMvc.perform(get(SYSTEM_STATUS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backend.service").value("adaptive-rate-limiter"))
                .andExpect(jsonPath("$.rateLimiter.mode").value("STATIC"))
                .andExpect(jsonPath("$.rateLimiter.configuredMaxRequests").value(1))
                .andExpect(jsonPath("$.rateLimiter.windowSeconds").value(60.0))
                .andExpect(jsonPath("$.rateLimiter.store").value("MEMORY"))
                .andExpect(jsonPath("$.store.type").value("MEMORY"))
                .andExpect(jsonPath("$.store.available").value(true))
                .andExpect(jsonPath("$.store.verified").value(true))
                .andExpect(jsonPath("$.store.status").value("ready"))
                .andExpect(jsonPath("$.store.fallbackMode").doesNotExist())
                .andExpect(jsonPath("$.monitoring.schedulerStatus").value("scheduled"))
                .andExpect(jsonPath("$.monitoring.pendingWindows").isNumber())
                .andExpect(jsonPath("$.monitoring.lastCompletedAt").doesNotExist())
                .andExpect(jsonPath("$.monitoring.latestWindow").exists())
                .andExpect(jsonPath("$.ml.available").value(false))
                .andExpect(jsonPath("$.ml.status").value("unavailable"))
                .andExpect(jsonPath("$.ml.lastSuccessfulEvaluationAt").doesNotExist())
                .andExpect(jsonPath("$.ml.lastFailureAt").doesNotExist())
                .andExpect(jsonPath("$.ml.modelInfo").doesNotExist())
                .andExpect(jsonPath("$.adaptive.mode").value("STATIC"));
    }

    @Test
    @DisplayName("system status bypasses the rate limiter and does not pollute counters")
    void systemStatusDoesNotConsumeLimiterCapacity() throws Exception {
        long totalBefore = metrics.getTotalRequests();

        mockMvc.perform(get(SYSTEM_STATUS)).andExpect(status().isOk());
        mockMvc.perform(get(SYSTEM_STATUS)).andExpect(status().isOk());

        assertThat(metrics.getTotalRequests()).isEqualTo(totalBefore);
    }

    @Test
    @DisplayName("system status does not expose ML base URLs or secrets")
    void systemStatusDoesNotExposeSensitiveConfiguration() throws Exception {
        String body = mockMvc.perform(get(SYSTEM_STATUS))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .doesNotContain("baseUrl")
                .doesNotContain("base-url")
                .doesNotContain("127.0.0.1")
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("token");
    }
}
