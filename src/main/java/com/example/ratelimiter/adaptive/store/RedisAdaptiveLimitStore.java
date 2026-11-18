package com.example.ratelimiter.adaptive.store;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.ratelimiter.adaptive.AdaptiveEngineProperties;
import com.example.ratelimiter.config.RedisRateLimitProperties;

/**
 * Redis-backed adaptive limit storage. Reads are performed on every limiter
 * decision, so a limit change written by one instance is observed by the
 * others.
 *
 * <p>If Redis is unavailable the last successfully read or written value is
 * retained locally. This keeps the limiter deterministic during an outage
 * while Redis is degraded.
 */
public class RedisAdaptiveLimitStore implements AdaptiveLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisAdaptiveLimitStore.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisRateLimitProperties properties;
    private final int minLimit;
    private final int maxLimit;
    private final AtomicInteger lastKnownLimit;
    private final AtomicLong lastFailureLogAt = new AtomicLong();

    public RedisAdaptiveLimitStore(StringRedisTemplate redisTemplate,
                                   RedisRateLimitProperties properties,
                                   AdaptiveEngineProperties engineProperties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.properties = Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(engineProperties, "engineProperties");
        this.minLimit = engineProperties.getMinLimit();
        this.maxLimit = engineProperties.getMaxLimit();
        validateBounds(minLimit, maxLimit);
        this.lastKnownLimit = new AtomicInteger(clamp(engineProperties.getInitialLimit()));
    }

    @Override
    public int getCurrentLimit() {
        try {
            String value = redisTemplate.opsForValue().get(limitKey());
            if (value == null) {
                redisTemplate.opsForValue().setIfAbsent(limitKey(), Integer.toString(lastKnownLimit.get()));
                return lastKnownLimit.get();
            }
            int parsed;
            try {
                parsed = Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                log.warn("Invalid value '{}' for Redis adaptive limit; restoring last known limit {}", value,
                        lastKnownLimit.get());
                redisTemplate.opsForValue().set(limitKey(), Integer.toString(lastKnownLimit.get()));
                return lastKnownLimit.get();
            }
            int clamped = clamp(parsed);
            if (clamped != parsed) {
                redisTemplate.opsForValue().set(limitKey(), Integer.toString(clamped));
            }
            lastKnownLimit.set(clamped);
            return clamped;
        } catch (RuntimeException ex) {
            logFailure(ex);
            return lastKnownLimit.get();
        }
    }

    @Override
    public int updateLimit(int proposedLimit) {
        int clamped = clamp(proposedLimit);
        lastKnownLimit.set(clamped);
        try {
            redisTemplate.opsForValue().set(limitKey(), Integer.toString(clamped));
        } catch (RuntimeException ex) {
            logFailure(ex);
        }
        return lastKnownLimit.get();
    }

    String limitKey() {
        return properties.getKeyPrefix() + ":limit:current";
    }

    private int clamp(int value) {
        return Math.max(minLimit, Math.min(maxLimit, value));
    }

    private void logFailure(RuntimeException ex) {
        long now = System.currentTimeMillis();
        long intervalMillis = Math.max(1, properties.getFailureLogIntervalSeconds()) * 1000;
        long previous = lastFailureLogAt.get();
        if (now - previous >= intervalMillis && lastFailureLogAt.compareAndSet(previous, now)) {
            log.warn("Redis adaptive-limit store unavailable; retaining last known limit {}: {}",
                    lastKnownLimit.get(), ex.toString());
        }
    }

    private static void validateBounds(int minLimit, int maxLimit) {
        if (minLimit < 1 || maxLimit < minLimit) {
            throw new IllegalArgumentException("Invalid adaptive limit bounds");
        }
    }
}
