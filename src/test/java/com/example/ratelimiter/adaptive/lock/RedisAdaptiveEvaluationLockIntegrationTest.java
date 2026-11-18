package com.example.ratelimiter.adaptive.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.ratelimiter.adaptive.AdaptiveIntegrationProperties;
import com.example.ratelimiter.config.RedisRateLimitProperties;
import com.example.ratelimiter.support.RedisTestServer;

class RedisAdaptiveEvaluationLockIntegrationTest {

    private static RedisTestServer server;
    private static RedisRateLimitProperties properties;

    @BeforeAll
    static void startRedis() {
        server = RedisTestServer.startOrNull();
        if (server == null) {
            return;
        }
        properties = new RedisRateLimitProperties();
        properties.setKeyPrefix("rl-test-lock");
    }

    @AfterAll
    static void stopRedis() {
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    void flushRedis() {
        Assumptions.assumeTrue(server != null, RedisTestServer.UNAVAILABLE_REASON);
        server.flush();
    }

    @Test
    void onlyOneInstanceCanHoldTheLock() {
        RedisAdaptiveEvaluationLock first = lock(5);
        RedisAdaptiveEvaluationLock second = lock(5);

        assertThat(first.tryAcquire()).isTrue();
        assertThat(second.tryAcquire()).isFalse();

        first.release();

        assertThat(second.tryAcquire()).isTrue();
    }

    @Test
    void releaseDoesNotDeleteALockOwnedByAnotherInstance() {
        RedisAdaptiveEvaluationLock lock = lock(5);
        assertThat(lock.tryAcquire()).isTrue();

        server.template().opsForValue().set(lock.lockKey(), "other-instance", Duration.ofSeconds(30));

        lock.release();

        assertThat(server.template().opsForValue().get(lock.lockKey())).isEqualTo("other-instance");
    }

    @Test
    void expiredLockCanBeAcquiredByAnotherInstance() throws InterruptedException {
        RedisAdaptiveEvaluationLock first = lock(1);
        RedisAdaptiveEvaluationLock second = lock(1);

        assertThat(first.tryAcquire()).isTrue();
        Thread.sleep(1_200);

        assertThat(second.tryAcquire()).isTrue();
        first.release();

        assertThat(server.template().hasKey(first.lockKey())).isTrue();
    }

    private static RedisAdaptiveEvaluationLock lock(long ttlSeconds) {
        AdaptiveIntegrationProperties integrationProperties = new AdaptiveIntegrationProperties();
        integrationProperties.setEvaluationLockTtlSeconds(ttlSeconds);
        return new RedisAdaptiveEvaluationLock(server.template(), properties, integrationProperties);
    }
}
