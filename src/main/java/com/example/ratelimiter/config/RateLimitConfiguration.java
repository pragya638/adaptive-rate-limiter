package com.example.ratelimiter.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.ratelimiter.adaptive.AdaptiveEngineProperties;
import com.example.ratelimiter.adaptive.store.AdaptiveLimitStore;
import com.example.ratelimiter.ratelimit.DynamicRateLimiter;
import com.example.ratelimiter.ratelimit.RateLimiter;
import com.example.ratelimiter.ratelimit.StaticRateLimiter;
import com.example.ratelimiter.ratelimit.store.CounterStore;

@Configuration
public class RateLimitConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(name = "adaptive.enabled", havingValue = "false", matchIfMissing = true)
    public RateLimiter staticRateLimiter(RateLimitProperties properties, Clock clock, CounterStore counterStore) {
        return new StaticRateLimiter(properties.getMaxRequests(), properties.getWindow(), clock, counterStore);
    }

    @Bean
    @ConditionalOnProperty(name = "adaptive.enabled", havingValue = "true")
    public DynamicRateLimiter dynamicRateLimiter(RateLimitProperties rateLimitProperties,
            AdaptiveEngineProperties adaptiveProperties, Clock clock,
            CounterStore counterStore, AdaptiveLimitStore limitStore) {
        return new DynamicRateLimiter(
                adaptiveProperties.getMinLimit(),
                adaptiveProperties.getMaxLimit(),
                rateLimitProperties.getWindow(),
                clock,
                counterStore,
                limitStore);
    }
}
