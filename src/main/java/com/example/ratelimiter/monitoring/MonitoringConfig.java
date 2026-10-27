package com.example.ratelimiter.monitoring;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MonitoringConfig {

    @Bean
    public Clock monitoringClock() {
        return Clock.systemUTC();
    }
}
