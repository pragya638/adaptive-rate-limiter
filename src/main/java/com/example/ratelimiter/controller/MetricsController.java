package com.example.ratelimiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ratelimiter.monitoring.MonitoringService;
import com.example.ratelimiter.monitoring.TrafficMetricsDto;

@RestController
@RequestMapping("/api")
public class MetricsController {
    private final MonitoringService monitoringService;

    public MetricsController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/metrics")
    public TrafficMetricsDto metrics() {
        return monitoringService.getMetrics();
    }
}
