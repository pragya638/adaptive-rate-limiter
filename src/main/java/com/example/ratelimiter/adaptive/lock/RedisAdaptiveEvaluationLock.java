package com.example.ratelimiter.adaptive.lock;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.example.ratelimiter.adaptive.AdaptiveIntegrationProperties;
import com.example.ratelimiter.config.RedisRateLimitProperties;

/**
 * Redis leader lock implemented with {@code SET NX PX}. Release uses a
 * compare-and-delete Lua script so an expired lock owned by another instance
 * is never removed.
 */
public class RedisAdaptiveEvaluationLock implements AdaptiveEvaluationLock {

    private static final Logger log = LoggerFactory.getLogger(RedisAdaptiveEvaluationLock.class);

    private static final String RELEASE_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            else
              return 0
            end
            """;

    private static final DefaultRedisScript<Long> RELEASE =
            new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisRateLimitProperties properties;
    private final Duration ttl;
    private final AtomicReference<String> currentToken = new AtomicReference<>();
    private final AtomicLong lastFailureLogAt = new AtomicLong();

    public RedisAdaptiveEvaluationLock(StringRedisTemplate redisTemplate,
                                       RedisRateLimitProperties properties,
                                       AdaptiveIntegrationProperties integrationProperties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.properties = Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(integrationProperties, "integrationProperties");
        long ttlSeconds = integrationProperties.getEvaluationLockTtlSeconds();
        if (ttlSeconds < 1) {
            throw new IllegalArgumentException("adaptive.evaluation-lock-ttl-seconds must be at least 1");
        }
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public boolean tryAcquire() {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey(), token, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                currentToken.set(token);
                return true;
            }
            return false;
        } catch (RuntimeException ex) {
            logFailure(ex);
            return false;
        }
    }

    @Override
    public void release() {
        String token = currentToken.getAndSet(null);
        if (token == null) {
            return;
        }
        try {
            redisTemplate.execute(RELEASE, List.of(lockKey()), token);
        } catch (RuntimeException ex) {
            logFailure(ex);
        }
    }

    String lockKey() {
        return properties.getKeyPrefix() + ":adaptive:lock";
    }

    private void logFailure(RuntimeException ex) {
        long now = System.currentTimeMillis();
        long intervalMillis = Math.max(1, properties.getFailureLogIntervalSeconds()) * 1000;
        long previous = lastFailureLogAt.get();
        if (now - previous >= intervalMillis && lastFailureLogAt.compareAndSet(previous, now)) {
            log.warn("Redis adaptive-evaluation lock unavailable; skipping adaptive evaluation: {}",
                    ex.toString());
        }
    }
}
