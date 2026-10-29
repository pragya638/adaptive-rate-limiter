# Adaptive API Rate Limiter (AI-Driven)

Final Year Project: **AI-Driven Adaptive API Rate Limiting Using Traffic Trend Analysis and Anomaly Detection**.

This repository contains a Spring Boot static rate limiter (Phase 1), traffic monitoring and EWMA trend analysis (Phase 3), and a standalone Python FastAPI Isolation Forest anomaly detection service (ml-service, Phase 4). The long-term objective is to combine static throttling, traffic trend analysis, anomaly detection, and an adaptive decision engine to adjust limits dynamically while enforcing safety bounds. The complete adaptive system is still a work in progress; current implementation status is listed below.


## Table of Contents

- [Problem Statement & Motivation](#problem-statement--motivation)
- [Project Objectives](#project-objectives)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [High-Level Architecture](#high-level-architecture)
- [Implementation Status](#implementation-status)
- [Phase 1: Spring Boot Static Rate Limiter](#phase-1-spring-boot-static-rate-limiter)
- [Phase 3: Traffic Monitoring & EWMA](#phase-3-traffic-monitoring--ewma)
- [Phase 4: Python FastAPI Isolation Forest Service (ml-service)](#phase-4-python-fastapi-isolation-forest-service-ml-service)
- [Repository Setup & Quick Start](#repository-setup--quick-start)
- [Running Tests](#running-tests)
- [Service URLs](#service-urls)
- [Example API Calls](#example-api-calls)
- [Limitations](#limitations)
- [Future Work](#future-work)
- [References](#references)


## Phase 5: Adaptive Decision Engine & Integration (In Progress)

### Overview
Phase 5 introduces an adaptive decision layer that evaluates recent traffic against anomaly detection and applies conservative adjustments with safety bounds.

### Architecture (Mermaid)
`mermaid
flowchart TD
  A[HTTP Requests] --> B[RateLimitFilter]
  B --> C[StaticRateLimiter / Window]
  C --> D[MonitoringService]
  D --> E[TrafficMetricsDto]
  E --> F[MetricsToFeaturesMapper / TrafficAssessment]
  F --> G[AnomalyServiceClient
  G[HTTP to ml-service]]
  G --> H[IsolationForest API
  H[POST /detect]]
  H --> I[HttpAnomalyAssessmentProvider]
  I --> J[AdaptiveDecisionEngine]
  J --> K[AdaptiveLimitAdvisor
  K[clamps to bounds]]
  L[AdaptiveEvaluationScheduler
  L[@Scheduled]] -.-> E
  L -.-> K
` 

### Key Components (added)
- AdaptiveDecisionEngine, DecisionAction, AdaptiveDecision, TrafficAssessment, AnomalyAssessment (conservative fallback when unavailable)
- AnomalyAssessmentProvider + HttpAnomalyAssessmentProvider using JDK HttpClient
- client/AnomalyServiceClient, client/TrafficFeaturesRequest, client/DetectResponseDto
- mapping/MetricsToFeaturesMapper
- limit/AdaptiveLimitAdvisor (thread-safe clamping; advisory only)
- integration/AdaptiveEvaluationScheduler (periodic, catches all exceptions, disabled by default)

### Configuration (application.yml)
`yaml
ml-service:
  base-url: http://localhost:8000
  connect-timeout-ms: 1000
  read-timeout-ms: 1000

adaptive:
  enabled: false
  evaluation-interval-seconds: 30
` 

### Safety & Fallbacks
- If ML service unreachable/timeout/invalid/503/422 -> AnomalyAssessment.unavailable(); engine uses conservative defaults
- Bounds enforced by AdaptiveEngineProperties and AdaptiveLimitAdvisor
- Scheduler runs in background, never blocks request path, all exceptions caught
- Advisor is advisory only (does not mutate StaticRateLimiter in current wiring)

### Testing
- Phase 5 unit tests: src/test/java/com/example/ratelimiter/adaptive/AdaptiveDecisionEngineTest.java (11 tests) - all passing

### Remaining Limitations (Phase 5)
- Dynamic application to StaticRateLimiter not yet implemented
- End-to-end with live ml-service not verified by default tests
- Full automation still under development
