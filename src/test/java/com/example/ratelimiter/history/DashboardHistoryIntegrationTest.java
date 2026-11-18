package com.example.ratelimiter.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ratelimiter.monitoring.TrafficMetrics;
import com.example.ratelimiter.monitoring.TrafficMetricsDto;

@SpringBootTest(properties = {
        "adaptive.enabled=false",
        "rate-limit.max-requests=1",
        "rate-limit.window=60s",
        "monitoring.observation-interval=1h",
        "dashboard-history.max-records=2"
})
@AutoConfigureMockMvc
class DashboardHistoryIntegrationTest {

    private static final String HISTORY = "/api/history";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardHistoryService historyService;

    @Autowired
    private DashboardHistoryStore historyStore;

    @Autowired
    private TrafficMetrics metrics;

    @BeforeEach
    void clearHistory() {
        historyStore.clear();
    }

    @Test
    @DisplayName("history endpoint reports an honest empty snapshot")
    void historyEndpointStartsEmpty() throws Exception {
        mockMvc.perform(get(HISTORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storage").value("MEMORY"))
                .andExpect(jsonPath("$.survivesRestart").value(false))
                .andExpect(jsonPath("$.maxRecords").value(2))
                .andExpect(jsonPath("$.traffic.length()").value(0))
                .andExpect(jsonPath("$.anomalies.length()").value(0))
                .andExpect(jsonPath("$.decisions.length()").value(0))
                .andExpect(jsonPath("$.limits.length()").value(0));
    }

    @Test
    @DisplayName("history endpoint is bounded and returns records oldest to newest")
    void historyEndpointReturnsBoundedChronologicalRecords() throws Exception {
        for (int sequence = 1; sequence <= 3; sequence++) {
            historyService.recordTrafficWindow(window(sequence));
        }

        mockMvc.perform(get(HISTORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traffic.length()").value(2))
                .andExpect(jsonPath("$.traffic[0].windowSequence").value(2))
                .andExpect(jsonPath("$.traffic[1].windowSequence").value(3));
    }

    @Test
    @DisplayName("history polling bypasses the limiter and does not pollute counters")
    void historyPollingDoesNotConsumeLimiterCapacity() throws Exception {
        long totalBefore = metrics.getTotalRequests();

        mockMvc.perform(get(HISTORY)).andExpect(status().isOk());
        mockMvc.perform(get(HISTORY)).andExpect(status().isOk());
        mockMvc.perform(get(HISTORY)).andExpect(status().isOk());

        assertThat(metrics.getTotalRequests()).isEqualTo(totalBefore);
    }

    private TrafficMetricsDto window(long sequence) {
        return new TrafficMetricsDto(
                1000L, 990L, 10L, 0.01, 0.83, 0.8,
                Instant.parse("2026-01-01T00:00:30Z"),
                25L, 20L, 5L, 0.08, sequence,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:30Z"), 30.0);
    }
}
