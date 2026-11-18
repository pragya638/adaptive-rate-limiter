package com.example.ratelimiter.adaptive.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.example.ratelimiter.adaptive.AdaptiveIntegrationProperties;
import com.example.ratelimiter.config.RedisRateLimitProperties;

class RedisAdaptiveEvaluationLockTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisRateLimitProperties properties;
    private AdaptiveIntegrationProperties integrationProperties;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        properties = new RedisRateLimitProperties();
        properties.setKeyPrefix("test");

        integrationProperties = new AdaptiveIntegrationProperties();
        integrationProperties.setEvaluationLockTtlSeconds(5);
    }

    @Test
    void acquiresOnlyWhenSetNxSucceeds() {
        when(valueOperations.setIfAbsent(eq("test:adaptive:lock"), anyString(), eq(Duration.ofSeconds(5))))
                .thenReturn(true);
        RedisAdaptiveEvaluationLock lock =
                new RedisAdaptiveEvaluationLock(redisTemplate, properties, integrationProperties);

        assertThat(lock.tryAcquire()).isTrue();
        verify(valueOperations).setIfAbsent(eq("test:adaptive:lock"), anyString(), eq(Duration.ofSeconds(5)));
    }

    @Test
    void doesNotAcquireWhenAnotherInstanceHoldsTheLock() {
        when(valueOperations.setIfAbsent(eq("test:adaptive:lock"), anyString(), eq(Duration.ofSeconds(5))))
                .thenReturn(false);
        RedisAdaptiveEvaluationLock lock =
                new RedisAdaptiveEvaluationLock(redisTemplate, properties, integrationProperties);

        assertThat(lock.tryAcquire()).isFalse();
    }

    @Test
    void skipsAdaptiveEvaluationWhenRedisIsUnavailable() {
        when(valueOperations.setIfAbsent(eq("test:adaptive:lock"), anyString(), eq(Duration.ofSeconds(5))))
                .thenThrow(new RedisConnectionFailureException("down"));
        RedisAdaptiveEvaluationLock lock =
                new RedisAdaptiveEvaluationLock(redisTemplate, properties, integrationProperties);

        assertThat(lock.tryAcquire()).isFalse();
    }

    @Test
    void releaseIsNoOpWhenLockWasNotAcquired() {
        RedisAdaptiveEvaluationLock lock =
                new RedisAdaptiveEvaluationLock(redisTemplate, properties, integrationProperties);

        lock.release();

        verify(redisTemplate, never()).execute(any(), anyList(), any());
    }

    @Test
    void releasesOwnedLock() {
        when(valueOperations.setIfAbsent(eq("test:adaptive:lock"), anyString(), eq(Duration.ofSeconds(5))))
                .thenReturn(true);
        RedisAdaptiveEvaluationLock lock =
                new RedisAdaptiveEvaluationLock(redisTemplate, properties, integrationProperties);
        lock.tryAcquire();

        lock.release();

        verify(redisTemplate).execute(any(), eq(java.util.List.of("test:adaptive:lock")), anyString());
    }
}
