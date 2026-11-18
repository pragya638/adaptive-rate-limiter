package com.example.ratelimiter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.ratelimiter.adaptive.AdaptiveEngineProperties;
import com.example.ratelimiter.adaptive.AdaptiveIntegrationProperties;
import com.example.ratelimiter.adaptive.lock.AdaptiveEvaluationLock;
import com.example.ratelimiter.adaptive.lock.NoopAdaptiveEvaluationLock;
import com.example.ratelimiter.adaptive.lock.RedisAdaptiveEvaluationLock;
import com.example.ratelimiter.adaptive.store.AdaptiveLimitStore;
import com.example.ratelimiter.adaptive.store.InMemoryAdaptiveLimitStore;
import com.example.ratelimiter.adaptive.store.RedisAdaptiveLimitStore;
import com.example.ratelimiter.ratelimit.store.CounterStore;
import com.example.ratelimiter.ratelimit.store.InMemoryCounterStore;
import com.example.ratelimiter.ratelimit.store.RedisCounterStore;

/**
 * Selects the counter, adaptive-limit and leader-lock implementations.
 *
 * <p>The in-memory beans are retained as the default and require no Redis
 * connection. Redis beans are only created when
 * {@code rate-limit.store=redis}; startup still succeeds during a Redis outage
 * because all Redis operations happen lazily and have local fallbacks.
 */
@Configuration
public class RateLimitStoreConfiguration {

    @Bean
    @ConditionalOnProperty(name = "rate-limit.store", havingValue = "memory", matchIfMissing = true)
    public CounterStore inMemoryCounterStore() {
        return new InMemoryCounterStore();
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.store", havingValue = "redis")
    public CounterStore redisCounterStore(StringRedisTemplate redisTemplate,
            RedisRateLimitProperties properties) {
        return new RedisCounterStore(redisTemplate, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.store", havingValue = "memory", matchIfMissing = true)
    public AdaptiveLimitStore inMemoryAdaptiveLimitStore(AdaptiveEngineProperties properties) {
        return new InMemoryAdaptiveLimitStore(
                properties.getInitialLimit(), properties.getMinLimit(), properties.getMaxLimit());
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.store", havingValue = "redis")
    public AdaptiveLimitStore redisAdaptiveLimitStore(StringRedisTemplate redisTemplate,
            RedisRateLimitProperties redisProperties, AdaptiveEngineProperties engineProperties) {
        return new RedisAdaptiveLimitStore(redisTemplate, redisProperties, engineProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.store", havingValue = "memory", matchIfMissing = true)
    public AdaptiveEvaluationLock noopAdaptiveEvaluationLock() {
        return new NoopAdaptiveEvaluationLock();
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.store", havingValue = "redis")
    public AdaptiveEvaluationLock redisAdaptiveEvaluationLock(StringRedisTemplate redisTemplate,
            RedisRateLimitProperties redisProperties,
            AdaptiveIntegrationProperties integrationProperties) {
        return new RedisAdaptiveEvaluationLock(redisTemplate, redisProperties, integrationProperties);
    }
}
