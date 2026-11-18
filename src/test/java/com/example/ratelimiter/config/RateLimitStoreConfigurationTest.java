package com.example.ratelimiter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

class RateLimitStoreConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RateLimitStoreConfiguration.class)
            .withBean(RateLimitProperties.class, RateLimitProperties::new)
            .withBean(RedisRateLimitProperties.class, RedisRateLimitProperties::new)
            .withBean(AdaptiveEngineProperties.class, AdaptiveEngineProperties::new)
            .withBean(AdaptiveIntegrationProperties.class, AdaptiveIntegrationProperties::new)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    void memoryIsTheDefaultAndCreatesNoRedisBackedStores() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CounterStore.class);
            assertThat(context).hasSingleBean(AdaptiveLimitStore.class);
            assertThat(context).hasSingleBean(AdaptiveEvaluationLock.class);
            assertThat(context.getBean(CounterStore.class)).isInstanceOf(InMemoryCounterStore.class);
            assertThat(context.getBean(AdaptiveLimitStore.class)).isInstanceOf(InMemoryAdaptiveLimitStore.class);
            assertThat(context.getBean(AdaptiveEvaluationLock.class)).isInstanceOf(NoopAdaptiveEvaluationLock.class);
        });
    }

    @Test
    void redisSelectionCreatesRedisBackedStores() {
        contextRunner.withPropertyValues("rate-limit.store=redis").run(context -> {
            assertThat(context).hasSingleBean(CounterStore.class);
            assertThat(context).hasSingleBean(AdaptiveLimitStore.class);
            assertThat(context).hasSingleBean(AdaptiveEvaluationLock.class);
            assertThat(context.getBean(CounterStore.class)).isInstanceOf(RedisCounterStore.class);
            assertThat(context.getBean(AdaptiveLimitStore.class)).isInstanceOf(RedisAdaptiveLimitStore.class);
            assertThat(context.getBean(AdaptiveEvaluationLock.class)).isInstanceOf(RedisAdaptiveEvaluationLock.class);
        });
    }
}
