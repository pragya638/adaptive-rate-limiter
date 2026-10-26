package com.example.ratelimiter.ratelimit;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.monitoring.TrafficMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP adapter for {@link RateLimiter}. All decision making lives in the
 * limiter; this filter only maps the result onto headers and a status code.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String HEADER_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_REMAINING = "X-RateLimit-Remaining";

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final TrafficMetrics trafficMetrics;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties, ObjectMapper objectMapper,
            TrafficMetrics trafficMetrics) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.trafficMetrics = trafficMetrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (matchesAny(properties.getExcludePaths(), path)) {
            return true;
        }
        return !matchesAny(properties.getIncludePaths(), path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        boolean isMetricsEndpoint = "/api/metrics".equals(request.getRequestURI())
                || pathMatcher.match("/api/metrics", request.getRequestURI());

        if (!isMetricsEndpoint) {
            trafficMetrics.recordRequest();
        }

        RateLimitDecision decision = rateLimiter.tryAcquire(resolveClientKey(request));

        response.setHeader(HEADER_LIMIT, Integer.toString(decision.limit()));
        response.setHeader(HEADER_REMAINING, Integer.toString(decision.remaining()));

        if (!decision.allowed()) {
            if (!isMetricsEndpoint) {
                trafficMetrics.recordRejectedByRateLimit();
            }
            writeTooManyRequests(request, response, decision);
            return;
        }
        if (!isMetricsEndpoint) {
            trafficMetrics.recordAllowed();
        }

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException ex) {
            if (!isMetricsEndpoint && response.getStatus() < 400) {
                trafficMetrics.recordOtherError();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (!isMetricsEndpoint && response.getStatus() < 400) {
                trafficMetrics.recordOtherError();
            }
            throw ex;
        }

        if (!isMetricsEndpoint) {
            int status = response.getStatus();
            if (status >= 400 && status != HttpStatus.TOO_MANY_REQUESTS.value()) {
                trafficMetrics.recordOtherError();
            }
        }
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response,
            RateLimitDecision decision) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        body.put("message", "Rate limit exceeded. Try again in " + decision.retryAfterSeconds() + " second(s).");
        body.put("path", request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String resolveClientKey(HttpServletRequest request) {
        String header = request.getHeader(properties.getClientIdHeader());
        if (StringUtils.hasText(header)) {
            return header;
        }
        return request.getRemoteAddr();
    }

    private boolean matchesAny(List<String> patterns, String path) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}
