package com.example.ratelimiter.web;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.example.ratelimiter.ratelimit.RateLimiter;

/**
 * End-to-end tests of the Phase 1 static limiter over HTTP.
 * The configured limit is overridden to 3 requests per 60 seconds so the
 * tests are fast and deterministic.
 */
@SpringBootTest(properties = {
        "rate-limit.max-requests=3",
        "rate-limit.window=60s"
})
@AutoConfigureMockMvc
class RateLimitingHttpTest {

    private static final String DEMO = "/api/demo";
    private static final String HEALTH = "/api/health";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    void resetLimiter() {
        rateLimiter.reset();
    }

    @Test
    @DisplayName("GET /api/health returns 200 and is not rate limited")
    void healthEndpointReturnsOkWithoutRateLimiting() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get(HEALTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.service").value("adaptive-rate-limiter"));
        }
    }

    @Test
    @DisplayName("GET /api/demo allows requests while the limit is not exceeded")
    void demoEndpointAllowsRequestsWithinLimit() throws Exception {
        mockMvc.perform(get(DEMO))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "2"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get(DEMO))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "1"));

        mockMvc.perform(get(DEMO))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }

    @Test
    @DisplayName("GET /api/demo returns HTTP 429 once the limit is exceeded")
    void demoEndpointReturnsTooManyRequestsWhenLimitExceeded() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(DEMO)).andExpect(status().isOk());
        }

        mockMvc.perform(get(DEMO))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string("Retry-After", not(blankOrNullString())))
                .andExpect(header().string("Retry-After", greaterThanOrEqualTo("1")))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value(DEMO));
    }

    @Test
    @DisplayName("Different client ids are limited independently")
    void differentClientsAreLimitedIndependently() throws Exception {
        for (int i = 0; i < 3; i++) {
            performAs("client-one").andExpect(status().isOk());
        }
        performAs("client-one").andExpect(status().isTooManyRequests());

        performAs("client-two").andExpect(status().isOk());
    }

    private ResultActions performAs(String clientId) throws Exception {
        return mockMvc.perform(get(DEMO).header("X-Client-Id", clientId));
    }
}
