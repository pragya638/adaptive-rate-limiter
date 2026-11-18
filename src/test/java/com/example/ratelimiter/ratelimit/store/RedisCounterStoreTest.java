package com.example.ratelimiter.ratelimit.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.ratelimiter.config.RedisRateLimitProperties;

class RedisCounterStoreTest {

    private StringRedisTemplate redisTemplate;
    private RedisRateLimitProperties properties;
    private InMemoryCounterStore fallback;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        properties = new RedisRateLimitProperties();
        properties.setKeyPrefix("test");
        properties.setTtlGrace(Duration.ofMillis(100));
        properties.setFailureMode(RedisRateLimitProperties.FailureMode.OPEN);
        fallback = new InMemoryCounterStore();
    }

    @Test
    void returnsRedisCountOnSuccessfulIncrement() {
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(7L);
        RedisCounterStore store = new RedisCounterStore(redisTemplate, properties, fallback);

        CounterIncrement result = store.increment("client", 1_000, 500);

        assertThat(result.count()).isEqualTo(7);
        assertThat(result.degraded()).isFalse();
        assertThat(store.redisKey("client", 1_000)).isEqualTo("test:fw:1000:client");
    }

    @Test
    void failOpenUsesLocalFallbackAndMarksResultDegraded() {
        when(redisTemplate.execute(any(), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));
        RedisCounterStore store = new RedisCounterStore(redisTemplate, properties, fallback);

        CounterIncrement first = store.increment("client", 1_000, 500);
        CounterIncrement second = store.increment("client", 1_000, 500);

        assertThat(first.count()).isEqualTo(1);
        assertThat(second.count()).isEqualTo(2);
        assertThat(first.degraded()).isTrue();
        assertThat(second.degraded()).isTrue();
    }

    @Test
    void failClosedRejectsRequestsWhileRedisIsDown() {
        properties.setFailureMode(RedisRateLimitProperties.FailureMode.CLOSED);
        when(redisTemplate.execute(any(), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));
        RedisCounterStore store = new RedisCounterStore(redisTemplate, properties, fallback);

        CounterIncrement result = store.increment("client", 1_000, 500);

        assertThat(result.count()).isEqualTo(Long.MAX_VALUE);
        assertThat(result.degraded()).isTrue();
    }

    @Test
    void pingReportsRedisAvailabilityWithoutTouchingCounters() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("PONG");
        RedisCounterStore store = new RedisCounterStore(redisTemplate, properties, fallback);

        assertThat(store.ping()).isTrue();
    }

    @Test
    void pingReportsFailureWhenRedisIsUnavailable() {
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new RedisConnectionFailureException("down"));
        RedisCounterStore store = new RedisCounterStore(redisTemplate, properties, fallback);

        assertThat(store.ping()).isFalse();
    }

    @Test
    void resetDeletesMatchingRedisKeysAndClearsFallback() {
        when(redisTemplate.keys("test:fw:*")).thenReturn(Set.of("test:fw:1000:client"));
        RedisCounterStore store = new RedisCounterStore(redisTemplate, properties, fallback);
        fallback.increment("client", 1_000, 500);

        store.reset();

        verify(redisTemplate).delete(Set.of("test:fw:1000:client"));
        assertThat(fallback.increment("client", 1_000, 500).count()).isEqualTo(1);
    }
}
