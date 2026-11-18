package com.example.ratelimiter.ratelimit.store;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.example.ratelimiter.config.RedisRateLimitProperties;

/**
 * Redis-backed fixed-window counter store.
 *
 * <p>The increment and first-increment expiry are executed by a single Lua
 * script so concurrent instances cannot create a counter without a TTL.
 */
public class RedisCounterStore implements CounterStore {

    private static final Logger log = LoggerFactory.getLogger(RedisCounterStore.class);

    private static final String INCREMENT_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """;

    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(INCREMENT_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisRateLimitProperties properties;
    private final CounterStore fallback;
    private final AtomicLong lastFailureLogAt = new AtomicLong();

    public RedisCounterStore(StringRedisTemplate redisTemplate, RedisRateLimitProperties properties) {
        this(redisTemplate, properties, new InMemoryCounterStore());
    }

    RedisCounterStore(StringRedisTemplate redisTemplate, RedisRateLimitProperties properties, CounterStore fallback) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        if (properties.getKeyPrefix() == null || properties.getKeyPrefix().isBlank()) {
            throw new IllegalArgumentException("rate-limit.redis.key-prefix must not be blank");
        }
        if (properties.getTtlGrace() == null || properties.getTtlGrace().isNegative()) {
            throw new IllegalArgumentException("rate-limit.redis.ttl-grace must be zero or positive");
        }
        Objects.requireNonNull(properties.getFailureMode(), "rate-limit.redis.failure-mode");
    }

    @Override
    public CounterIncrement increment(String clientKey, long windowStartEpochMillis, long windowMillis) {
        String redisKey = redisKey(clientKey, windowStartEpochMillis);
        long ttlMillis = ttlMillis(windowMillis);
        try {
            Long count = redisTemplate.execute(INCREMENT, List.of(redisKey), Long.toString(ttlMillis));
            if (count == null) {
                throw new IllegalStateException("Redis returned no counter value");
            }
            return new CounterIncrement(count, false);
        } catch (RuntimeException ex) {
            logFailure(ex);
            CounterIncrement fallbackResult = fallback.increment(clientKey, windowStartEpochMillis, windowMillis);
            if (properties.getFailureMode() == RedisRateLimitProperties.FailureMode.CLOSED) {
                return new CounterIncrement(Long.MAX_VALUE, true);
            }
            return new CounterIncrement(fallbackResult.count(), true);
        }
    }

    @Override
    public void reset() {
        try {
            Set<String> keys = redisTemplate.keys(properties.getKeyPrefix() + ":fw:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (RuntimeException ex) {
            logFailure(ex);
        } finally {
            fallback.reset();
        }
    }

    public boolean ping() {
        try {
            String response = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(response);
        } catch (RuntimeException ex) {
            logFailure(ex);
            return false;
        }
    }

    String redisKey(String clientKey, long windowStartEpochMillis) {
        return properties.getKeyPrefix() + ":fw:" + windowStartEpochMillis + ":" + clientKey;
    }

    private long ttlMillis(long windowMillis) {
        long grace = properties.getTtlGrace().toMillis();
        if (grace > Long.MAX_VALUE - windowMillis) {
            return Long.MAX_VALUE;
        }
        return Math.max(1, windowMillis + grace);
    }

    private void logFailure(RuntimeException ex) {
        long now = System.currentTimeMillis();
        long intervalMillis = Math.max(1, properties.getFailureLogIntervalSeconds()) * 1000;
        long previous = lastFailureLogAt.get();
        if (now - previous >= intervalMillis && lastFailureLogAt.compareAndSet(previous, now)) {
            log.warn("Redis rate-limit store unavailable; using '{}' fallback: {}",
                    properties.getFailureMode(), ex.toString());
        }
    }
}
