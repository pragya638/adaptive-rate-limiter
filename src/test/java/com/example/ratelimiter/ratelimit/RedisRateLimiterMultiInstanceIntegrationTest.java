package com.example.ratelimiter.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.ratelimiter.adaptive.AdaptiveEngineProperties;
import com.example.ratelimiter.adaptive.store.RedisAdaptiveLimitStore;
import com.example.ratelimiter.config.RedisRateLimitProperties;
import com.example.ratelimiter.ratelimit.store.RedisCounterStore;
import com.example.ratelimiter.support.RedisTestServer;

class RedisRateLimiterMultiInstanceIntegrationTest {

    private static RedisTestServer server;
    private static RedisCounterStore counterStore;
    private static RedisAdaptiveLimitStore limitStore;
    private static DynamicRateLimiter instanceA;
    private static DynamicRateLimiter instanceB;

    @BeforeAll
    static void startRedis() {
        server = RedisTestServer.startOrNull();
        if (server == null) {
            return;
        }

        RedisRateLimitProperties redisProperties = new RedisRateLimitProperties();
        redisProperties.setKeyPrefix("rl-test-multi");
        redisProperties.setTtlGrace(Duration.ZERO);

        AdaptiveEngineProperties engineProperties = new AdaptiveEngineProperties();
        engineProperties.setMinLimit(1);
        engineProperties.setMaxLimit(100);
        engineProperties.setInitialLimit(5);

        counterStore = new RedisCounterStore(server.template(), redisProperties);
        limitStore = new RedisAdaptiveLimitStore(server.template(), redisProperties, engineProperties);

        Clock clock = Clock.systemUTC();
        Duration window = Duration.ofSeconds(30);
        instanceA = new DynamicRateLimiter(1, 100, window, clock, counterStore, limitStore);
        instanceB = new DynamicRateLimiter(1, 100, window, clock, counterStore, limitStore);
    }

    @AfterAll
    static void stopRedis() {
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    void resetSharedState() {
        Assumptions.assumeTrue(server != null, RedisTestServer.UNAVAILABLE_REASON);
        server.flush();
        limitStore.updateLimit(5);
    }

    @Test
    void countersAreSharedAcrossApplicationInstances() {
        for (int i = 0; i < 5; i++) {
            assertThat(instanceA.tryAcquire("shared-client").allowed()).isTrue();
        }

        RateLimitDecision rejected = instanceB.tryAcquire("shared-client");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
    }

    @Test
    void adaptiveLimitUpdatesAreVisibleToOtherInstances() {
        for (int i = 0; i < 5; i++) {
            instanceA.tryAcquire("another-client");
        }

        assertThat(instanceA.updateLimit(7)).isEqualTo(7);
        assertThat(instanceB.getCurrentLimit()).isEqualTo(7);
        assertThat(instanceB.tryAcquire("another-client").allowed()).isTrue();
        assertThat(instanceB.tryAcquire("another-client").allowed()).isTrue();
        assertThat(instanceB.tryAcquire("another-client").allowed()).isFalse();
    }
}
