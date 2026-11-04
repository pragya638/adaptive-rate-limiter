package com.example.ratelimiter.adaptive;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdaptiveEngineProperties.class)
public class AdaptiveEngineConfiguration {
}
