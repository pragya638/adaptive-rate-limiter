# Adaptive API Rate Limiter (AI-Driven)

Final Year Project: **AI-Driven Adaptive API Rate Limiting Using Traffic Trend Analysis and Anomaly Detection**.

This repository contains a Spring Boot static rate limiter (Phase 1) and a standalone Python FastAPI Isolation Forest anomaly detection service (`ml-service`, Phase 4). The long-term objective is to combine static throttling, traffic trend analysis, anomaly detection, and an adaptive decision engine to adjust limits dynamically while enforcing safety bounds. The complete adaptive system is still a work in progress; current implementation status is listed below.


## Table of Contents

- [Problem Statement & Motivation](#problem-statement--motivation)
- [Project Objectives](#project-objectives)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [High-Level Architecture](#high-level-architecture)
- [Implementation Status](#implementation-status)
- [Phase 1: Spring Boot Static Rate Limiter](#phase-1-spring-boot-static-rate-limiter)
- [Phase 4: Python FastAPI Isolation Forest Service (ml-service)](#phase-4-python-fastapi-isolation-forest-service-ml-service)
- [Repository Setup & Quick Start](#repository-setup--quick-start)
- [Running Tests](#running-tests)
- [Service URLs](#service-urls)
- [Example API Calls](#example-api-calls)
- [Limitations](#limitations)
- [Future Work](#future-work)
- [References](#references)

## Problem Statement & Motivation

APIs are vulnerable to traffic spikes, abuse, and anomalous behaviour that can degrade performance or cause service outages. Traditional static fixed-window rate limiters enforce a constant quota per client regardless of context, which is often either too restrictive under normal surges or too permissive during malicious/anomalous traffic. An adaptive approach that considers traffic trends and anomaly signals can respond more intelligently while respecting safety bounds.

## Project Objectives

- Implement a configurable static API rate limiter using Spring Boot.
- Monitor traffic and compute trend signals (EWMA) to inform future decisions (planned).
- Detect anomalous traffic windows using an unsupervised anomaly detection model (Isolation Forest).
- Design an adaptive decision engine to adjust rate limits based on combined signals (planned).
- Provide a dashboard for visualising traffic and system behaviour (planned).
- Compare static and adaptive rate limiting under controlled scenarios (planned).

## Key Features

- **Static fixed-window throttling** (Spring Boot) with clear 429 responses and standard headers.
- **Per-client isolation** with IP fallback and configurable identity header.
- **Unsupervised anomaly detection** via Isolation Forest (FastAPI service) on four traffic features.
- **Robust input validation** rejecting negative, out-of-range, and non-finite (NaN/Infinity) values.
- **Deterministic, reproducible synthetic training** for development/testing.
- **Independent, testable services** with clear JSON contracts.
- **Thin HTTP adapters** separating core logic (rate limiting / ML) from web concerns.

## Technology Stack

| Layer | Technology |
|---|---|
| Backend (API & rate limiting) | Java 17, Spring Boot 3.5.x, Spring Web |
| ML/Anomaly Detection Service | Python 3.14.x, FastAPI, Uvicorn, scikit-learn 1.9.x, NumPy 2.5.x, Pydantic 2.14.x |
| Testing | JUnit/MockMvc (Spring), pytest, httpx (Python) |
| Build/Tooling | Maven Wrapper, pip, venv |
| Data Store (planned) | Redis (for distributed counters/trend state) |
| Frontend (planned) | React |

## High-Level Architecture

```mermaid
flowchart TD
  A[Client Request] --> B[Spring Boot API]
  B --> C{Rate Limiting Layer\n(Static: Fixed Window)\nPlanned: Adaptive}
  C -- Allowed --> D[Business Logic / Controllers]
  C -- Rejected (429) --> E[Client]
  B --> F[Traffic Metrics Collector\n(Planned)]
  F --> G[EWMA Trend Analysis\n(Phase 3)]
  F --> H[Python Isolation Forest Service\nml-service /detect\n(Implemented)]
  H --> I[Anomaly Score + Prediction]
  G --> J[Adaptive Decision Engine\n(Phase 5 - Pending)]
  I --> J
  J --> C
  K[React Dashboard\n(Planned)] --> F
  K --> H
```

**Note:** Connections from Spring Boot to `ml-service`, adaptive decision-making, Redis-backed distributed counters, and the React dashboard are **planned** (not implemented). The Python service runs independently and can be called by the backend in a future phase; it does not currently drive adaptive adjustments. Phase 3 adds traffic monitoring and EWMA trend signals in Spring Boot, but these are not yet wired to adaptive rate limit adjustment.


## Implementation Status

| Component | Status | Notes |
|---|---|---|
| Phase 1: Static Spring Boot rate limiter | Implemented & tested | Fixed-window, per-client, configurable, HTTP 429 with Retry-After, headers. |
| Phase 2: Redis integration | Pending verification | Verify repository for Redis-backed counter/state before finalising. |
| Phase 3: Traffic monitoring & EWMA | Implemented | Traffic metrics, scheduled EWMA updates, `/api/metrics` endpoint. |

| Phase 4: Python FastAPI Isolation Forest service (`ml-service/`) | Implemented, tested & merged into master | 68 tests passed (1 Starlette deprecation warning from pinned httpx). Endpoints: `/health`, `/detect`, `/model/info`. Input validation rejects NaN/Infinity. Reported test results from ml-service audit (68 passed). |
| Phase 5: Adaptive decision engine | Pending | Not implemented. |
| React dashboard | Pending verification | Verify presence/implementation in repository. |
| End-to-end integration & comparative evaluation | Pending | Static vs adaptive comparison requires integration. |
## Phase 1: Spring Boot Static Rate Limiter

### Overview

- Java 17, Spring Boot 3.5.x
- `GET `/api/health`` – health/sanity endpoint (excluded from rate limiting)
- `GET /api/demo` – sample protected endpoint
- Static, configurable rate limiter (max requests per fixed time window, per client)
- Returns **HTTP 429 Too Many Requests** with `Retry-After` when the limit is exceeded
- Rate-limiting logic lives in its own package and is completely separate from controllers
- Automated tests for allowed requests and for requests exceeding the limit

Explicitly **not** implemented in this phase: Redis, Python, Isolation Forest, EWMA, React.

### Prerequisites (Phase 1)

- **JDK 17** (tested with Temurin 17.0.17). Verify with `java -version`.
- **No Maven installation required** – the Maven Wrapper (`mvnw` / `mvnw.cmd`) is included and downloads
  Maven 3.9.16 on first use. If you prefer a local Maven, 3.9+ works too.

## Project structure

```
adaptive-rate-limiter/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/                 # Maven Wrapper
├── README.md
└── src
    ├── main
    │   ├── java/com/example/ratelimiter
    │   │   ├── AdaptiveRateLimiterApplication.java
    │   │   ├── config
    │   │   │   ├── RateLimitProperties.java      # rate-limit.* configuration binding
    │   │   │   └── RateLimitConfiguration.java   # Clock + RateLimiter beans
    │   │   ├── ratelimit
    │   │   │   ├── RateLimiter.java              # algorithm interface
    │   │   │   ├── RateLimitDecision.java        # immutable decision result
    │   │   │   ├── StaticRateLimiter.java        # fixed-window algorithm (no web code)
    │   │   │   └── RateLimitFilter.java          # HTTP adapter: headers + 429 response
    │   │   └── controller
    │   │       ├── HealthController.java         # GET `/api/health`
    │   │       └── DemoController.java           # GET /api/demo
    │   └── resources/application.yml
    └── test/java/com/example/ratelimiter
        ├── ratelimit/StaticRateLimiterTest.java  # algorithm unit tests
        └── web/RateLimitingHttpTest.java         # endpoint + 429 integration tests
```

### Configuration

All limits are configurable in `src/main/resources/application.yml`:

```yaml
rate-limit:
  enabled: true
  max-requests: 5                 # allowed requests per client per window
  window: 30s                     # fixed window length (ISO-8601 duration, e.g. 10s, 1m)
  include-paths:                  # paths that are rate limited
    - /api/**
  exclude-paths:                  # paths that bypass the limiter
    - `/api/health`
  client-id-header: X-Client-Id   # client identity; falls back to caller IP
```

You can override any value at runtime without editing the file. For example (Windows PowerShell):

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--rate-limit.max-requests=100 --rate-limit.window=1m"
```

On macOS/Linux:

```bash
./mvnw spring-boot:run --args="--rate-limit.max-requests=100 --rate-limit.window=1m"
```

### How the rate limiter works

`RateLimitFilter` (a `OncePerRequestFilter`) intercepts requests whose path matches `include-paths`
and asks the `RateLimiter` bean for a decision. The algorithm itself (`StaticRateLimiter`) is a
thread-safe fixed-window counter keyed by client id or IP address:

- Every client gets a counter that resets at each window boundary.
- Requests `1..max-requests` are allowed and receive `X-RateLimit-Limit` / `X-RateLimit-Remaining` headers.
- The next request within the same window is rejected with `429` and a `Retry-After` header.

Because the algorithm has no web dependencies, a future adaptive implementation can replace it without
touching controllers or the filter.

### Running the application

Windows (PowerShell):

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux:

```bash
./mvnw spring-boot:run
```

The API starts on <http://localhost:8080>. 

### Try the endpoints (Phase 1)

```powershell
# Health (not rate limited)
curl.exe http://localhost:8080`/api/health`

# Demo endpoint (rate limited; a client id makes the identity explicit)
1..7 | ForEach-Object {
  curl.exe -i -s -H "X-Client-Id: demo-user" http://localhost:8080/api/demo |
    Select-String "HTTP/|X-RateLimit|Retry-After"
}
```

With the default `max-requests: 5` per `30s`, the first five calls return `200`, the sixth and seventh
return `429` with a `Retry-After` header.

## Running the tests

Windows (PowerShell):

```powershell
.\mvnw.cmd test
```

macOS / Linux:

```bash
./mvnw test
```

To run a single test class:

```powershell
.\mvnw.cmd test "-Dtest=RateLimitingHttpTest"
```

### What the tests cover

- `StaticRateLimiterTest` – requests up to the limit are allowed, the next request is rejected,
  clients are isolated, quota resets when the window rolls over, `reset()` clears state.
- `RateLimitingHttpTest` – ``/api/health`` always returns `200`; `/api/demo` allows requests within the
  (test-configured) limit and returns `429` with `Retry-After` once it is exceeded; clients are limited
  independently.

## Build a runnable jar

## Repository Setup & Quick Start

### Prerequisites

- **JDK 17** (for Spring Boot)
- **Python 3.11+** (verified 3.14.2 for `ml-service`) with `pip` and `venv`
- **Git**

### Repository Structure (high-level)

```
adaptive-rate-limiter/
├── src/                # Spring Boot application (Phase 1)
├── pom.xml
├── mvnw, mvnw.cmd
├── ml-service/         # FastAPI Isolation Forest service (Phase 4)
└── README.md
```

### Spring Boot setup and startup

```powershell
# Windows PowerShell
.\mvnw.cmd spring-boot:run
```
```bash
# macOS/Linux
./mvnw spring-boot:run
```

### Maven test commands

```powershell
.\mvnw.cmd test
.\mvnw.cmd test "-Dtest=RateLimitingHttpTest"
```

### Python service setup and startup

See full details in [ml-service/README.md](ml-service/README.md). Quick start:

```powershell
# Windows PowerShell (from ml-service/)
cd ml-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1  # or call .\.venv\Scripts\python.exe directly
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000
```
```bash
# macOS/Linux
cd ml-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

### Python test commands

```powershell
# Windows PowerShell
.\.venv\Scripts\python.exe -m pytest
```
```bash
# macOS/Linux
python -m pytest
```

## Service URLs

| Service | URL | Notes |
|---|---|---|
| Spring Boot API | http://localhost:8080 | Rate limited endpoints under `/api/**` (e.g. ``/api/health``, `/api/demo`) |
| ML Service | http://localhost:8000 | `GET /health`, `POST /detect`, `GET /model/info`, `GET /docs`, `GET /openapi.json` |

## Example API Calls

### Spring Boot

```powershell
curl.exe http://localhost:8080`/api/health`
curl.exe -i -s -H "X-Client-Id: demo-user" http://localhost:8080/api/demo
```

### ML Service

```powershell
curl.exe http://127.0.0.1:8000/health
curl.exe http://127.0.0.1:8000/model/info
curl.exe -X POST http://127.0.0.1:8000/detect -H "Content-Type: application/json" -d "{\"rrequest_rate\":20.0,\"error_rate\":0.02,\"burst_ratio\":1.0,\"rrequest_count\":20}"
curl.exe -X POST http://127.0.0.1:8000/detect -H "Content-Type: application/json" -d "{\"rrequest_rate\":800.0,\"error_rate\":0.95,\"burst_ratio\":10.0,\"rrequest_count\":800}"
```

```bash
# macOS/Linux
curl -s http://127.0.0.1:8000/health
curl -s -X POST http://127.0.0.1:8000/detect -H "Content-Type: application/json" -d '{"rrequest_rate":20.0,"error_rate":0.02,"burst_ratio":1.0,"rrequest_count":20}'
```
## Limitations

- Synthetic training data: The ML model is trained on deterministic synthetic \"normal\" traffic. It is not a substitute for evaluation on real production traffic; no real-world precision/recall/F1 is claimed.
- Phase-wise status: Redis integration, EWMA/trend monitoring, adaptive decision engine, React dashboard, end-to-end integration, and comparative evaluation are not fully verified/completed as of this update (see Implementation Status). Do not assume automatic integration between Spring Boot and ml-service at runtime.
- Scope of detection: The service reports anomalies based on four features only; it does not enforce rate limits or modify Spring Boot behaviour.
- Non-finite/invalid inputs: Explicitly rejected (NaN/Infinity) to avoid silent model misuse; validation errors return 422 with structured details.
- Dependencies: The Python test suite emits one Starlette deprecation warning due to pinned httpx/starlette compatibility (not a functional issue).
- Production readiness: Not claimed. The system requires integration testing, labelled data (if available), and controlled comparison before deployment.

## Future Work

- Redis integration (Phase 2): Distributed counters for multi-instance deployments.
- Traffic monitoring & EWMA (Phase 3): Track request rates/errors/trends over time to inform adaptation.
- Adaptive decision engine (Phase 5): Combine anomaly signals with trend/EWMA under safety bounds to adjust limits dynamically.
- React dashboard: Visualise traffic, anomalies, limits, and metrics.
- End-to-end integration: Wire Spring Boot metrics to ml-service with safe fallbacks.
- Evaluation: Measure legitimate-request rejection rate, anomaly detection precision/recall/F1 (if labelled data available), p50/p95 latency, throughput, and detection/adaptation time under controlled scenarios.
- Model retraining: Replace synthetic data with real traffic and re-evaluate.

## References

- [Spring Boot 3.5.x Documentation](https://docs.spring.io/spring-boot/docs/3.5.x/reference/html/)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [scikit-learn Isolation Forest](https://scikit-learn.org/stable/modules/generated/sklearn.ensemble.IsolationForest.html)
- [ml-service/README.md](ml-service/README.md) � detailed Python setup, configuration, and API reference.


## Phase 3: Traffic Monitoring & EWMA

- Traffic metrics collected per request (`totalRequests`, `allowedRequests`, `rejectedByRateLimit`/`rejectedRequests`, `otherErrorResponses`).
- Current request rate (`request_rate`) computed over the configured observation interval by `closeWindow(interval)`; counters reset per window.
- Error rate = (rejectedByRateLimit + otherErrorResponses) / totalRequests (0 if `totalRequests` == 0).
- EWMA: S(t) = alpha * X(t) + (1-alpha) * S(t-1), alpha configurable via `monitoring.ewma-alpha` (0 < alpha <= 1). EWMA is updated by the scheduled `MonitoringService.updateObservations()` using the observation interval.
- Observation interval configurable via `monitoring.observation-interval` (default `30s`).
- `GET /api/metrics` returns read-only metrics via `MetricsController` (`MonitoringService.getMetrics()`) and does not reset counters or advance EWMA. `/api/health` and `/api/metrics` are excluded from monitoring and rate limiting and do not pollute counters.
- EWMA is traffic smoothing (not an anomaly detector). Future mapping to Isolation Forest: `request_rate`, `error_rate`, `burst_ratio`, `request_count` (baseline policy to be defined).
- Validation: `monitoring.ewma-alpha` must be in (0, 1] and `monitoring.observation-interval` must be valid; invalid configuration is rejected.


