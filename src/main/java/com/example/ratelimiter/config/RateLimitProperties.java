package com.example.ratelimiter.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration for the (currently static) rate limiter.
 * Values are bound from the {@code rate-limit.*} keys in application.yml.
 */
@Validated
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /** Master switch, useful for debugging and tests. */
    private boolean enabled = true;

    /** Maximum number of requests allowed per client within one window. */
    @Min(1)
    private int maxRequests = 5;

    /** Length of the fixed time window. */
    @NotNull
    private Duration window = Duration.ofSeconds(30);

    /** Ant-style paths that are rate limited. */
    private List<String> includePaths = new ArrayList<>(List.of("/api/**"));

    /** Ant-style paths that are always allowed (checked before includePaths). */
    private List<String> excludePaths = new ArrayList<>(List.of("/api/health"));

    /** Header used to identify a client; falls back to the caller's IP address. */
    private String clientIdHeader = "X-Client-Id";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    public List<String> getIncludePaths() {
        return includePaths;
    }

    public void setIncludePaths(List<String> includePaths) {
        this.includePaths = includePaths;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public String getClientIdHeader() {
        return clientIdHeader;
    }

    public void setClientIdHeader(String clientIdHeader) {
        this.clientIdHeader = clientIdHeader;
    }
}
