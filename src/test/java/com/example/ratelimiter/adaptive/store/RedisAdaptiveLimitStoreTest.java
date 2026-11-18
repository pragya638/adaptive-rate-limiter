package com.example.ratelimiter.adaptive.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.example.ratelimiter.adaptive.AdaptiveEngineProperties;
import com.example.ratelimiter.config.RedisRateLimitProperties;

class RedisAdaptiveLimitStoreTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisRateLimitProperties properties;
    private AdaptiveEngineProperties engineProperties;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        properties = new RedisRateLimitProperties();
        properties.setKeyPrefix("test");

        engineProperties = new AdaptiveEngineProperties();
        engineProperties.setMinLimit(1);
        engineProperties.setMaxLimit(100);
        engineProperties.setInitialLimit(5);
    }

    @Test
    void readsLimitWrittenByAnotherInstance() {
        when(valueOperations.get("test:limit:current")).thenReturn("42");
        RedisAdaptiveLimitStore store = new RedisAdaptiveLimitStore(redisTemplate, properties, engineProperties);

        assertThat(store.getCurrentLimit()).isEqualTo(42);
    }

    @Test
    void initializesMissingLimitKey() {
        when(valueOperations.get("test:limit:current")).thenReturn(null);
        RedisAdaptiveLimitStore store = new RedisAdaptiveLimitStore(redisTemplate, properties, engineProperties);

        assertThat(store.getCurrentLimit()).isEqualTo(5);
        verify(valueOperations).setIfAbsent("test:limit:current", "5");
    }

    @Test
    void clampsAndPersistsUpdates() {
        RedisAdaptiveLimitStore store = new RedisAdaptiveLimitStore(redisTemplate, properties, engineProperties);

        assertThat(store.updateLimit(500)).isEqualTo(100);
        verify(valueOperations).set("test:limit:current", "100");
    }

    @Test
    void retainsLastKnownLimitWhenRedisIsUnavailable() {
        when(valueOperations.get("test:limit:current")).thenReturn("17");
        RedisAdaptiveLimitStore store = new RedisAdaptiveLimitStore(redisTemplate, properties, engineProperties);
        assertThat(store.getCurrentLimit()).isEqualTo(17);

        when(valueOperations.get("test:limit:current"))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(store.getCurrentLimit()).isEqualTo(17);
    }

    @Test
    void returnsClampedValueWhenTheWriteFails() {
        doThrow(new RedisConnectionFailureException("down"))
                .when(valueOperations).set("test:limit:current", "25");
        RedisAdaptiveLimitStore store = new RedisAdaptiveLimitStore(redisTemplate, properties, engineProperties);

        assertThat(store.updateLimit(25)).isEqualTo(25);
        assertThat(store.getCurrentLimit()).isEqualTo(25);
    }
}
