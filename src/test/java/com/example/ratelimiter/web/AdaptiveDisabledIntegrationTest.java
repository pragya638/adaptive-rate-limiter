package com.example.ratelimiter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ratelimiter.adaptive.AdaptiveLimitAdvisor;
import com.example.ratelimiter.adaptive.integration.AdaptiveEvaluationScheduler;
import com.example.ratelimiter.ratelimit.DynamicRateLimiter;
import com.example.ratelimiter.ratelimit.RateLimiter;
import com.example.ratelimiter.ratelimit.StaticRateLimiter;

@SpringBootTest(properties = {
        "adaptive.enabled=false",
        "rate-limit.max-requests=2",
        "rate-limit.window=60s"
})
@AutoConfigureMockMvc
class AdaptiveDisabledIntegrationTest {

    private static final String DEMO = "/api/demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void resetLimiter() {
        rateLimiter.reset();
    }

    @Test
    @DisplayName("adaptive disabled keeps the static limiter and does not create adaptive beans")
    void adaptiveDisabledUsesStaticLimiter() {
        assertThat(rateLimiter).isInstanceOf(StaticRateLimiter.class);
        assertThat(applicationContext.getBeansOfType(DynamicRateLimiter.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AdaptiveLimitAdvisor.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AdaptiveEvaluationScheduler.class)).isEmpty();
    }

    @Test
    @DisplayName("adaptive disabled preserves the configured static HTTP behaviour")
    void adaptiveDisabledPreservesStaticHttpBehaviour() throws Exception {
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
