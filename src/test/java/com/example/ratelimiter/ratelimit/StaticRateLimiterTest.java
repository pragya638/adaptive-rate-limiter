package com.example.ratelimiter.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaticRateLimiterTest {

    private static final Duration WINDOW = Duration.ofSeconds(30);
    private static final int MAX_REQUESTS = 3;

    private TestClock clock;
    private StaticRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2026-10-17T12:00:00Z"));
        limiter = new StaticRateLimiter(MAX_REQUESTS, WINDOW, clock);
    }

    @Test
    @DisplayName("requests up to the configured limit are allowed")
    void allowsRequestsUpToConfiguredLimit() {
        for (int i = 1; i <= MAX_REQUESTS; i++) {
            RateLimitDecision decision = limiter.tryAcquire("client-a");

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.limit()).isEqualTo(MAX_REQUESTS);
            assertThat(decision.remaining()).isEqualTo(MAX_REQUESTS - i);
            assertThat(decision.retryAfterSeconds()).isZero();
        }
    }

    @Test
    @DisplayName("request after the limit is rejected with 429 semantics")
    void rejectsRequestAfterLimitIsExhausted() {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.tryAcquire("client-a");
        }

        RateLimitDecision decision = limiter.tryAcquire("client-a");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.remaining()).isZero();
        assertThat(decision.retryAfterSeconds()).isBetween(1L, WINDOW.toSeconds());
    }

    @Test
    @DisplayName("clients are limited independently of each other")
    void limitsAreIsolatedPerClient() {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.tryAcquire("client-a");
        }

        assertThat(limiter.tryAcquire("client-a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("client-b").allowed()).isTrue();
    }

    @Test
    @DisplayName("quota is refreshed when the window rolls over")
    void refreshesQuotaWhenWindowRollsOver() {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.tryAcquire("client-a");
        }
        assertThat(limiter.tryAcquire("client-a").allowed()).isFalse();

        clock.advanceBy(WINDOW);

        RateLimitDecision decision = limiter.tryAcquire("client-a");
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(MAX_REQUESTS - 1);
    }

    @Test
    @DisplayName("reset clears accumulated counters")
    void resetClearsState() {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.tryAcquire("client-a");
        }
        assertThat(limiter.tryAcquire("client-a").allowed()).isFalse();

        limiter.reset();

        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();
    }

    @Test
    @DisplayName("constructor rejects invalid configuration")
    void constructorRejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new StaticRateLimiter(0, WINDOW, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaticRateLimiter(MAX_REQUESTS, Duration.ZERO, clock))
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
