package com.example.ratelimiter.ratelimit.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.ratelimiter.config.RedisRateLimitProperties;
import com.example.ratelimiter.support.RedisTestServer;

class RedisCounterStoreIntegrationTest {

    private static RedisTestServer server;
    private static RedisCounterStore store;
    private static RedisRateLimitProperties properties;

    @BeforeAll
    static void startRedis() {
        server = RedisTestServer.startOrNull();
        if (server == null) {
            return;
        }
        properties = new RedisRateLimitProperties();
        properties.setKeyPrefix("rl-test-counter");
        properties.setTtlGrace(Duration.ZERO);
        store = new RedisCounterStore(server.template(), properties);
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
    void incrementsAtomicallyAndSetsTtlOnFirstIncrement() {
        CounterIncrement first = store.increment("client", 1_000, 200);
        CounterIncrement second = store.increment("client", 1_000, 200);

        String key = store.redisKey("client", 1_000);
        assertThat(first.count()).isEqualTo(1);
        assertThat(second.count()).isEqualTo(2);
        assertThat(server.template().hasKey(key)).isTrue();
        Long ttl = server.template().getExpire(key, TimeUnit.MILLISECONDS);
        assertThat(ttl).isNotNull().isBetween(1L, 250L);
    }

    @Test
    void expiresCountersAfterTheWindowAndGracePeriod() throws InterruptedException {
        store.increment("client", 2_000, 150);
        String key = store.redisKey("client", 2_000);

        assertThat(server.template().hasKey(key)).isTrue();
        Thread.sleep(300);

        assertThat(server.template().hasKey(key)).isFalse();
    }

    @Test
    void keepsWindowsSeparate() {
        store.increment("client", 3_000, 1_000);
        store.increment("client", 3_000, 1_000);

        assertThat(store.increment("client", 4_000, 1_000).count()).isEqualTo(1);
    }

    @Test
    void concurrentIncrementsAreNotLost() throws Exception {
        int threads = 10;
        int perThread = 40;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < threads * perThread; i++) {
                tasks.add(() -> store.increment("concurrent", 5_000, 1_000).count());
            }
            List<Future<Long>> results = executor.invokeAll(tasks);
            long max = results.stream().mapToLong(result -> {
                try {
                    return result.get();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }).max().orElse(0);
            assertThat(max).isEqualTo(threads * perThread);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void resetRemovesAllRateLimiterKeys() {
        store.increment("a", 6_000, 1_000);
        store.increment("b", 6_000, 1_000);

        store.reset();

        assertThat(server.template().keys("rl-test-counter:fw:*")).isEmpty();
    }
}
