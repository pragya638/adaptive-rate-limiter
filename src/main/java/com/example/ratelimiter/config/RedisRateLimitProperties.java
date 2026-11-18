package com.example.ratelimiter.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Redis-specific settings for the optional distributed counter store.
 *
 * <p>Connection details and command timeouts are configured through Spring
 * Boot's standard {@code spring.data.redis.*} properties. This class only
 * contains rate-limiter-specific behaviour.
 */
@Validated
@ConfigurationProperties(prefix = "rate-limit.redis")
public class RedisRateLimitProperties {

    public enum FailureMode {
        /**
         * Continue serving requests using the local in-memory fallback when
         * Redis is unavailable. Distributed limits are not guaranteed while
         * degraded.
         */
        OPEN,
        /**
         * Reject requests when Redis is unavailable. This protects the backend
         * at the cost of availability.
         */
        CLOSED
    }

    /** Prefix for every Redis key owned by the rate limiter. */
    @NotBlank
    private String keyPrefix = "rl";

    /**
     * Extra time added to the window TTL so late requests and small clock skew
     * do not lose counters before the window has really ended.
     */
    @NotNull
    private Duration ttlGrace = Duration.ofSeconds(30);

    /** Behaviour when Redis operations fail. */
    @NotNull
    private FailureMode failureMode = FailureMode.OPEN;

    /** Minimum interval between repeated Redis failure log messages. */
    @Min(1)
    private long failureLogIntervalSeconds = 30;

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getTtlGrace() {
        return ttlGrace;
    }

    public void setTtlGrace(Duration ttlGrace) {
        this.ttlGrace = ttlGrace;
    }

    public FailureMode getFailureMode() {
        return failureMode;
    }

    public void setFailureMode(FailureMode failureMode) {
        this.failureMode = failureMode;
    }

    public long getFailureLogIntervalSeconds() {
        return failureLogIntervalSeconds;
    }

    public void setFailureLogIntervalSeconds(long failureLogIntervalSeconds) {
        this.failureLogIntervalSeconds = failureLogIntervalSeconds;
    }
}
