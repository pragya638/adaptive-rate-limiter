package com.example.ratelimiter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ratelimiter.adaptive.AdaptiveLimitAdvisor;
import com.example.ratelimiter.adaptive.AnomalyAssessment;
import com.example.ratelimiter.adaptive.TrafficAssessment;
import com.example.ratelimiter.adaptive.integration.AdaptiveEvaluationScheduler;
import com.example.ratelimiter.monitoring.CompletedWindowStore;
import com.example.ratelimiter.monitoring.TrafficMetricsDto;
import com.example.ratelimiter.ratelimit.DynamicRateLimiter;
import com.example.ratelimiter.ratelimit.RateLimiter;
import com.example.ratelimiter.ratelimit.StaticRateLimiter;

@SpringBootTest(properties = {
        "adaptive.enabled=true",
        "rate-limit.max-requests=3",
        "rate-limit.window=60s",
        "adaptive-rate-limiter.min-limit=1",
        "adaptive-rate-limiter.max-limit=100",
        "adaptive-rate-limiter.initial-limit=3",
        "adaptive-rate-limiter.increase-step=1",
        "adaptive-rate-limiter.decrease-step=1",
        "adaptive-rate-limiter.anomaly-confirmation-windows=1",
        "adaptive-rate-limiter.growth-confirmation-windows=1",
        "adaptive.cooldown-seconds=0",
        "monitoring.observation-interval=1h",
        "ml-service.base-url=http://127.0.0.1:1",
        "ml-service.connect-timeout-ms=50",
        "ml-service.read-timeout-ms=50"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdaptiveEnabledIntegrationTest {

    private static final String DEMO = "/api/demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private DynamicRateLimiter dynamicRateLimiter;

    @Autowired
    private AdaptiveLimitAdvisor advisor;

    @Autowired
    private AdaptiveEvaluationScheduler scheduler;

    @Autowired
    private CompletedWindowStore completedWindowStore;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @Order(1)
    @DisplayName("adaptive enabled wires the dynamic limiter, advisor, and scheduler")
    void adaptiveEnabledWiresDynamicComponents() {
        assertThat(rateLimiter).isInstanceOf(DynamicRateLimiter.class);
        assertThat(dynamicRateLimiter.getCurrentLimit()).isEqualTo(3);
        assertThat(advisor).isNotNull();
        assertThat(scheduler).isNotNull();
        assertThat(applicationContext.getBeansOfType(StaticRateLimiter.class)).isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("ML failure is conservative and advisor updates are enforced over HTTP")
    void mlFailureIsConservativeAndUpdatesAreEnforced() throws Exception {
        completedWindowStore.publish(new TrafficMetricsDto(
                1000L, 990L, 10L, 0.01, 60.0, 55.0, Instant.parse("2026-11-04T00:00:00Z"),
                25L, 20L, 5L, 0.08, 1L,
                Instant.parse("2026-11-04T00:00:00Z"),
                Instant.parse("2026-11-04T00:00:30Z"), 30.0));
        assertThatCode(scheduler::evaluate).doesNotThrowAnyException();
        assertThat(dynamicRateLimiter.getCurrentLimit()).isEqualTo(3);

        advisor.evaluateAndApply(
                new TrafficAssessment(60.0, 55.0, 0.01, 1000L, 990L, 10L),
                new AnomalyAssessment(true, 0.8, "anomaly", "IsolationForest"));
        assertThat(dynamicRateLimiter.getCurrentLimit()).isEqualTo(2);

        dynamicRateLimiter.reset();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get(DEMO))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "2"));
        }

        mockMvc.perform(get(DEMO))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }
}
