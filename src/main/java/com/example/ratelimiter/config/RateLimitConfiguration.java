package com.example.ratelimiter.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.ratelimiter.ratelimit.RateLimiter;
import com.example.ratelimiter.ratelimit.StaticRateLimiter;

@Configuration
public class RateLimitConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RateLimiter rateLimiter(RateLimitProperties properties, Clock clock) {
        return new StaticRateLimiter(properties.getMaxRequests(), properties.getWindow(), clock);
    }
}
