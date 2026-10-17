# Adaptive Rate Limiter

Final-year project: **AI-Driven Adaptive API Rate Limiting Using Traffic Trend Analysis and Anomaly Detection**.

This repository currently contains **Phase 1**: a working Spring Boot REST API protected by a
**static rate limiter** (fixed-window counter). Later phases (trend analysis, anomaly detection, Redis,
dashboards) are intentionally out of scope here.

## Phase 1 scope

- Java 17, Spring Boot 3.5.x
- `GET /api/health` – health/sanity endpoint (excluded from rate limiting)
- `GET /api/demo` – sample protected endpoint
- Static, configurable rate limiter (max requests per fixed time window, per client)
- Returns **HTTP 429 Too Many Requests** with `Retry-After` when the limit is exceeded
- Rate-limiting logic lives in its own package and is completely separate from controllers
- Automated tests for allowed requests and for requests exceeding the limit

Explicitly **not** implemented in this phase: Redis, Python, Isolation Forest, EWMA, React.

## Prerequisites

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
    │   │       ├── HealthController.java         # GET /api/health
    │   │       └── DemoController.java           # GET /api/demo
    │   └── resources/application.yml
    └── test/java/com/example/ratelimiter
        ├── ratelimit/StaticRateLimiterTest.java  # algorithm unit tests
        └── web/RateLimitingHttpTest.java         # endpoint + 429 integration tests
```

## Configuration

All limits are configurable in `src/main/resources/application.yml`:

```yaml
rate-limit:
  enabled: true
  max-requests: 5                 # allowed requests per client per window
  window: 30s                     # fixed window length (ISO-8601 duration, e.g. 10s, 1m)
  include-paths:                  # paths that are rate limited
    - /api/**
  exclude-paths:                  # paths that bypass the limiter
    - /api/health
  client-id-header: X-Client-Id   # client identity; falls back to caller IP
```

You can override any value at runtime without editing the file, for example:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--rate-limit.max-requests=100 --rate-limit.window=1m"
```

## How the rate limiter works

`RateLimitFilter` (a `OncePerRequestFilter`) intercepts requests whose path matches `include-paths`
and asks the `RateLimiter` bean for a decision. The algorithm itself (`StaticRateLimiter`) is a
thread-safe fixed-window counter keyed by client id or IP address:

- Every client gets a counter that resets at each window boundary.
- Requests `1..max-requests` are allowed and receive `X-RateLimit-Limit` / `X-RateLimit-Remaining` headers.
- The next request within the same window is rejected with `429` and a `Retry-After` header.

Because the algorithm has no web dependencies, a future adaptive implementation can replace it without
touching controllers or the filter.

## Running the application

Windows (PowerShell):

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux:

```bash
./mvnw spring-boot:run
```

The API starts on <http://localhost:8080>.

### Try the endpoints

```powershell
# Health (not rate limited)
curl.exe http://localhost:8080/api/health

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
- `RateLimitingHttpTest` – `/api/health` always returns `200`; `/api/demo` allows requests within the
  (test-configured) limit and returns `429` with `Retry-After` once it is exceeded; clients are limited
  independently.

## Build a runnable jar

```powershell
.\mvnw.cmd clean package
java -jar target\adaptive-rate-limiter-0.1.0.jar
```
