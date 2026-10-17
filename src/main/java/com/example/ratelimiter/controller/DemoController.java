package com.example.ratelimiter.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample protected endpoint. The rate limiting is applied by
 * {@code RateLimitFilter} and is intentionally absent from this class.
 */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class DemoController {

    @GetMapping("/demo")
    public Map<String, Object> demo() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Hello from the adaptive rate limiter demo endpoint");
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
