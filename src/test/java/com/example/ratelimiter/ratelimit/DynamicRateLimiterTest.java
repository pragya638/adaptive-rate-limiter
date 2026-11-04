package com.example.ratelimiter.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DynamicRateLimiterTest {

    private static final Duration WINDOW = Duration.ofSeconds(30);

    private TestClock clock;
    private DynamicRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2026-11-04T00:00:00Z"));
        limiter = new DynamicRateLimiter(3, 1, 10, WINDOW, clock);
    }

    @Test
    @DisplayName("requests up to the current dynamic limit are allowed")
    void allowsRequestsUpToCurrentLimit() {
        for (int i = 1; i <= 3; i++) {
            RateLimitDecision decision = limiter.tryAcquire("client-a");
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.limit()).isEqualTo(3);
            assertThat(decision.remaining()).isEqualTo(3 - i);
        }

        assertThat(limiter.tryAcquire("client-a").allowed()).isFalse();
    }

    @Test
    @DisplayName("increasing the limit raises the quota for subsequent requests")
    void increasingLimitRaisesQuota() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("client-a");
        }
        assertThat(limiter.tryAcquire("client-a").allowed()).isFalse();

        assertThat(limiter.updateLimit(5)).isEqualTo(5);

        RateLimitDecision decision = limiter.tryAcquire("client-a");
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.limit()).isEqualTo(5);
        assertThat(decision.remaining()).isZero();
    }

    @Test
    @DisplayName("decreasing the limit denies requests that exceed the new quota")
    void decreasingLimitDeniesExcessRequests() {
        limiter.updateLimit(1);

        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();
        RateLimitDecision decision = limiter.tryAcquire("client-a");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.limit()).isEqualTo(1);
        assertThat(decision.remaining()).isZero();
    }

    @Test
    @DisplayName("limit updates are clamped to the configured bounds")
    void updatesAreClampedToBounds() {
        assertThat(limiter.updateLimit(100)).isEqualTo(10);
        assertThat(limiter.getCurrentLimit()).isEqualTo(10);
        assertThat(limiter.updateLimit(0)).isEqualTo(1);
        assertThat(limiter.getCurrentLimit()).isEqualTo(1);
    }

    @Test
    @DisplayName("initial limit is clamped to the configured bounds")
    void initialLimitIsClamped() {
        assertThat(new DynamicRateLimiter(50, 1, 10, WINDOW, clock).getCurrentLimit()).isEqualTo(10);
        assertThat(new DynamicRateLimiter(0, 1, 10, WINDOW, clock).getCurrentLimit()).isEqualTo(1);
    }

    @Test
    @DisplayName("clients are limited independently")
    void limitsAreIsolatedPerClient() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("client-a");
        }
        assertThat(limiter.tryAcquire("client-a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("client-b").allowed()).isTrue();
    }

    @Test
    @DisplayName("quota is refreshed when the window rolls over")
    void refreshesQuotaWhenWindowRollsOver() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("client-a");
        }
        assertThat(limiter.tryAcquire("client-a").allowed()).isFalse();

        clock.advanceBy(WINDOW);

        RateLimitDecision decision = limiter.tryAcquire("client-a");
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(2);
    }

    @Test
    @DisplayName("reset clears counters but keeps the current limit")
    void resetClearsCountersOnly() {
        limiter.updateLimit(7);
        for (int i = 0; i < 7; i++) {
            limiter.tryAcquire("client-a");
        }
        assertThat(limiter.tryAcquire("client-a").allowed()).isFalse();

        limiter.reset();

        assertThat(limiter.getCurrentLimit()).isEqualTo(7);
        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();
    }

    @Test
    @DisplayName("concurrent requests never exceed the configured limit")
    void concurrentRequestsRespectLimit() throws Exception {
        limiter = new DynamicRateLimiter(100, 1, 1000, WINDOW, clock);
        int threads = 8;
        int attemptsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                start.await();
                int allowed = 0;
                for (int j = 0; j < attemptsPerThread; j++) {
                    if (limiter.tryAcquire("shared-client").allowed()) {
                        allowed++;
                    }
                }
                return allowed;
            });
        }

        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(executor.submit(task));
        }
        start.countDown();

        int totalAllowed = 0;
        for (Future<Integer> future : futures) {
            totalAllowed += future.get();
        }
        executor.shutdownNow();

        assertThat(totalAllowed).isEqualTo(100);
    }

    @Test
    @DisplayName("constructor rejects invalid bounds and window")
    void constructorRejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new DynamicRateLimiter(1, 0, 10, WINDOW, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DynamicRateLimiter(1, 10, 1, WINDOW, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DynamicRateLimiter(1, 1, 10, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DynamicRateLimiter(1, 1, 10, WINDOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class TestClock extends Clock {

        private Instant instant;

        private TestClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceBy(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public long millis() {
            return instant.toEpochMilli();
        }
    }
}
