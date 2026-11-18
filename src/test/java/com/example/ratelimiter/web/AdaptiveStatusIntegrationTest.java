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

import com.example.ratelimiter.adaptive.AdaptiveLimitAdvisor;
import com.example.ratelimiter.adaptive.AnomalyAssessment;
import com.example.ratelimiter.adaptive.TrafficAssessment;
import com.example.ratelimiter.ratelimit.DynamicRateLimiter;

@SpringBootTest(properties = {
        "adaptive.enabled=true",
        "rate-limit.max-requests=5",
        "rate-limit.window=60s",
        "adaptive-rate-limiter.min-limit=1",
        "adaptive-rate-limiter.max-limit=20",
        "adaptive-rate-limiter.initial-limit=5",
        "adaptive-rate-limiter.increase-step=5",
        "adaptive-rate-limiter.decrease-step=10",
        "adaptive-rate-limiter.decrease-fraction=0.2",
        "adaptive-rate-limiter.anomaly-confirmation-windows=1",
        "adaptive-rate-limiter.growth-confirmation-windows=1",
        "adaptive.cooldown-seconds=0",
        "monitoring.observation-interval=1h",
        "ml-service.base-url=http://127.0.0.1:1",
        "ml-service.connect-timeout-ms=50",
        "ml-service.read-timeout-ms=50"
})
@AutoConfigureMockMvc
class AdaptiveStatusIntegrationTest {

    private static final String STATUS = "/api/adaptive/status";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdaptiveLimitAdvisor advisor;

    @Autowired
    private DynamicRateLimiter limiter;

    @Test
    @DisplayName("status exposes limits, mode, latest anomaly, window and adaptation time")
    void statusExposesAdaptiveState() throws Exception {
        TrafficAssessment traffic = new TrafficAssessment(50.0, 50.0, 0.01, 1000L, 990L, 10L);
        AnomalyAssessment anomaly = new AnomalyAssessment(true, 0.5, "anomaly", "IsolationForest");
        advisor.evaluateAndApply(traffic, anomaly);

        assertThat(limiter.getCurrentLimit()).isEqualTo(4);

        mockMvc.perform(get(STATUS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.mode").value("ADAPTIVE"))
                .andExpect(jsonPath("$.currentLimit").value(4))
                .andExpect(jsonPath("$.minLimit").value(1))
                .andExpect(jsonPath("$.maxLimit").value(20))
                .andExpect(jsonPath("$.initialLimit").value(5))
                .andExpect(jsonPath("$.lastEvaluationAt").isNotEmpty())
                .andExpect(jsonPath("$.lastAdaptationAt").isNotEmpty())
                .andExpect(jsonPath("$.latestAnomaly.available").value(true))
                .andExpect(jsonPath("$.latestAnomaly.isAnomaly").value(true))
                .andExpect(jsonPath("$.latestAnomaly.model").value("IsolationForest"))
                .andExpect(jsonPath("$.latestFeatureVector.requestRate").value(50.0))
                .andExpect(jsonPath("$.latestFeatureVector.errorRate").value(0.01))
                .andExpect(jsonPath("$.latestFeatureVector.burstRatio").value(1.0))
                .andExpect(jsonPath("$.latestFeatureVector.requestCount").value(1000))
                .andExpect(jsonPath("$.trafficWindow").exists())
                .andExpect(jsonPath("$.trafficWindow.sequence").isNumber())
                .andExpect(jsonPath("$.lastDecision.action").value("DECREASE_LIMIT"))
                .andExpect(jsonPath("$.lastDecision.reasonCode").value("ANOMALY_DETECTED"));
    }

    @Test
    @DisplayName("status does not expose ML service URLs or secret-like fields")
    void statusDoesNotExposeSensitiveConfiguration() throws Exception {
        String body = mockMvc.perform(get(STATUS))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .doesNotContain("baseUrl")
                .doesNotContain("base-url")
                .doesNotContain("ml-service")
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("token");
    }
}
