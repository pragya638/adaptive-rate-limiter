# AI-Driven Adaptive API Rate Limiter

**Final Year Project | B.Tech Computer Science and Engineering**

An anomaly-aware API rate-limiting system that combines traffic monitoring, EWMA-based trend analysis, and machine-learning-based anomaly assessment to support adaptive request-limit decisions.

---

## 1. Project Overview

Conventional API rate limiters enforce predefined request limits over a specified time window. Although this approach protects APIs against excessive requests, fixed limits may not respond appropriately to changing traffic patterns, sudden traffic bursts, or potentially anomalous request behaviour.

This project implements an adaptive API rate-limiting prototype that combines:

- Spring Boot for API request handling and rate-limit enforcement.
- Exponentially Weighted Moving Average (EWMA) for traffic trend analysis.
- Python FastAPI and Isolation Forest for anomaly assessment.
- An adaptive decision engine for bounded runtime limit adjustment.
- A dynamic rate limiter for enforcing updated request limits.

The project investigates the trade-off between protecting API availability and avoiding unnecessary rejection of legitimate traffic.

## 2. Research Focus

**Research Question:**

Can combining traffic trend analysis and machine-learning-based anomaly assessment with bounded adaptive rate-limit decisions provide a useful alternative to conventional fixed-window rate limiting under controlled API traffic scenarios?

The project does not claim to invent adaptive rate limiting or the Isolation Forest algorithm. Its focus is the implementation and experimental evaluation of an integrated approach.

## 3. System Architecture

The system follows a modular architecture consisting of a Spring Boot backend, a traffic monitoring module, a Python-based anomaly detection service, and an adaptive decision engine.

### 3.1 High-Level Architecture Diagram

```mermaid
flowchart TD
    A["API Clients"] --> B["Spring Boot Application"]
    B --> C["RateLimitFilter"]
    C --> D{"Rate Limiting Mode"}

    D -->|"Static Mode"| E["StaticRateLimiter"]
    D -->|"Adaptive Mode"| F["DynamicRateLimiter"]

    E --> G{"Request Allowed?"}
    F --> G

    G -->|"Yes"| H["API Endpoint"]
    G -->|"No"| I["HTTP 429 Too Many Requests"]

    H --> J["HTTP 200 Response"]

    C --> K["Traffic Metrics Collection"]
    K --> L["MonitoringService"]
    L --> M["EWMA Traffic Trend Analysis"]

    M --> N["Adaptive Evaluation Scheduler"]
    N --> O["Metrics to Features Mapper"]
    O --> P["Python FastAPI Service"]
    P --> Q["Isolation Forest"]
    Q --> R["Anomaly Assessment"]

    M --> S["Adaptive Decision Engine"]
    R --> S

    S --> T["Adaptive Limit Advisor"]
    T --> F

    U["Redis - Planned"] -.-> F
    V["React Dashboard - Planned"] -.-> L
    V -.-> S
```

### 3.2 Architecture Components

| Component | Responsibility |
|---|---|
| API Clients | Generate HTTP requests to access the protected API. |
| Spring Boot Application | Handles incoming API requests. |
| RateLimitFilter | Intercepts requests, identifies clients and applies rate-limit enforcement. |
| StaticRateLimiter | Enforces predefined fixed-window request limits. |
| DynamicRateLimiter | Enforces runtime-adjustable request limits in adaptive mode. |
| MonitoringService | Collects request counts and traffic statistics. |
| EWMA Calculator | Smooths traffic-rate observations and identifies traffic trends. |
| Adaptive Evaluation Scheduler | Periodically evaluates completed monitoring windows. |
| MetricsToFeaturesMapper | Converts monitoring data into features for anomaly assessment. |
| Python FastAPI Service | Provides the machine-learning anomaly detection API. |
| Isolation Forest | Assesses whether submitted traffic behaviour is potentially anomalous. |
| Adaptive Decision Engine | Evaluates traffic trends and anomaly assessments to determine bounded limit changes. |
| Adaptive Limit Advisor | Applies approved decisions to the dynamic rate limiter. |
| Redis (Planned) | Will provide shared rate-limit state across backend instances. |
| React Dashboard (Planned) | Will provide a visual interface for traffic and system monitoring. |

### 3.3 Request Processing Flow

1. An API client sends an HTTP request.
2. The Spring Boot `RateLimitFilter` intercepts the request.
3. The application identifies the client using its configured client-identification mechanism.
4. The selected rate limiter checks the client's request count against the current limit.
5. If the request is within the limit, it is forwarded to the API endpoint.
6. If the request exceeds the limit, the application returns HTTP 429.
7. Request activity and outcomes are recorded for traffic monitoring.

### 3.4 Adaptive Monitoring and Decision Flow

The adaptive process runs independently of the incoming request path.

1. `MonitoringService` collects request statistics over a configured observation window.
2. EWMA calculates a smoothed traffic trend.
3. `AdaptiveEvaluationScheduler` periodically reads the latest completed monitoring window.
4. `MetricsToFeaturesMapper` converts traffic metrics into features.
5. The Spring Boot application sends these features to the Python FastAPI anomaly detection service.
6. The Isolation Forest model returns an anomaly assessment.
7. `AdaptiveDecisionEngine` evaluates the traffic trend, anomaly assessment, and recovery conditions.
8. `AdaptiveLimitAdvisor` applies the decision subject to cooldown and configured safety bounds.
9. `DynamicRateLimiter` enforces the updated limit for subsequent requests.

If the ML service is unavailable, the system uses its configured conservative fallback behaviour.

## 4. Project Objectives

- Protect APIs against excessive request traffic.
- Enforce configurable request limits for individual clients.
- Collect request metrics, including total, allowed and rate-limited requests.
- Analyse traffic trends using EWMA.
- Detect potentially anomalous traffic using Isolation Forest.
- Integrate monitoring and anomaly assessments into an adaptive decision engine.
- Adjust request limits within predefined safety boundaries.
- Compare static and adaptive rate-limiting approaches through controlled experiments.

## 5. Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.5.x |
| Build Tool | Maven |
| Rate Limiting | Fixed-window and dynamic rate limiter |
| Traffic Monitoring | Spring Boot monitoring components |
| Trend Analysis | EWMA |
| Machine Learning | Isolation Forest |
| ML API | Python, FastAPI, Pydantic |
| Testing | JUnit, Spring Boot Test, pytest |
| Shared State | Redis — Planned |
| Frontend | React — Planned |

## 6. Implementation Status

| Phase | Component | Status |
|---|---|---|
| Phase 1 | Spring Boot static rate limiter | Implemented |
| Phase 2 | Redis integration | Pending |
| Phase 3 | Traffic monitoring and EWMA | Implemented |
| Phase 4 | Python FastAPI Isolation Forest service | Implemented independently |
| Phase 5 | Adaptive decision engine and integration | Implemented; live ML validation pending |
| Phase 6 | React monitoring dashboard | Pending |
| Phase 7 | End-to-end experiments and comparative evaluation | Pending |

**Note:** Update this table only after verifying the relevant code and tests.

## 7. Key Features

### Implemented

- Static fixed-window API rate limiting.
- Configurable request limits and observation windows.
- HTTP 429 responses when the request quota is exceeded.
- Rate-limit response headers.
- Client-based request counting.
- Traffic metrics collection.
- EWMA-based traffic trend analysis.
- Python FastAPI anomaly detection service.
- Isolation Forest-based anomaly assessment.
- Adaptive decision engine with bounded limit adjustments.
- Scheduled adaptive evaluation.
- Conservative fallback when anomaly assessment is unavailable.
- Automated backend and ML service tests.

### Planned or In Progress

- Redis-backed distributed rate-limit state.
- React monitoring dashboard.
- Live end-to-end validation against the running Python ML service.
- Controlled performance comparison of static and adaptive approaches.

## 8. Adaptive Decision Mechanism

The adaptive decision engine evaluates traffic conditions and anomaly assessments before recommending a change to the current request limit.

The intended decision behaviour includes:

- Traffic spikes may trigger a reduction in the current request limit.
- Sustained traffic growth is evaluated before making an adjustment.
- Anomalous traffic can trigger a conservative reduction.
- Normal traffic conditions support gradual recovery.
- Cooldown periods prevent excessively frequent limit changes.
- Minimum and maximum bounds prevent unsafe limit adjustments.

The current implementation uses a global adaptive limit rather than a separately maintained adaptive limit for each client. This is an identified limitation.

## 9. Machine Learning Component

The Python FastAPI service provides anomaly assessment through an Isolation Forest model.

### Main responsibilities

- Accept traffic-related feature values.
- Validate incoming request data.
- Perform anomaly assessment.
- Return the assessment through an API response.
- Expose service health and model information.

### API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Check ML service health |
| GET | `/model/info` | Retrieve model information |
| POST | `/detect` | Perform anomaly assessment |
| GET | `/docs` | Interactive API documentation |

**Model limitation:** The current model uses synthetic training data. Real-world anomaly detection performance has not yet been established.

## 10. API Endpoints

### Spring Boot Backend

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | Backend health check |
| GET | `/api/demo` | Protected demonstration endpoint |
| GET | `/api/metrics` | Retrieve traffic monitoring metrics |
| GET | `/api/adaptive/status` | Retrieve adaptive system status |

### Python FastAPI Service

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | ML service health |
| GET | `/model/info` | Model information |
| POST | `/detect` | Anomaly detection |
| GET | `/docs` | API documentation |

Check the current controller and schema files for the exact request and response formats.

## 11. Repository Structure

```text
adaptive-rate-limiter/
│
├── src/
│   ├── main/
│   │   ├── java/com/example/ratelimiter/
│   │   │   ├── adaptive/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── filter/
│   │   │   ├── monitoring/
│   │   │   └── ratelimit/
│   │   │
│   │   └── resources/
│   │
│   └── test/
│
├── ml-service/
│   ├── app/
│   │   ├── config.py
│   │   ├── detector.py
│   │   ├── main.py
│   │   └── schemas.py
│   │
│   ├── tests/
│   ├── requirements.txt
│   └── README.md
│
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```

This is a representative structure. Refer to the repository for the exact current file organisation.

## 12. Prerequisites

- Java Development Kit (JDK) 17 or compatible version.
- Python compatible with the ML service dependencies.
- Git.
- Internet connection for initial dependency installation.

Maven is available through the included Maven Wrapper.

## 13. Installation and Setup

### Clone the repository

```bash
git clone https://github.com/pragya638/adaptive-rate-limiter.git
cd adaptive-rate-limiter
```

### Start Spring Boot Backend

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

On Linux/macOS:

```bash
./mvnw spring-boot:run
```

Backend default URL:

`http://localhost:8080`

### Start Python ML Service

Open another terminal:

```powershell
cd ml-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload --port 8000
```

ML service default URL:

`http://localhost:8000`

Interactive API documentation:

`http://localhost:8000/docs`

## 14. Running Tests

### Spring Boot Tests

```powershell
.\mvnw.cmd clean test
```

### Python Tests

From the `ml-service` directory:

```powershell
python -m pytest
```

Run the tests again after pulling changes. Report the actual test counts from the latest execution rather than relying on an older result.

## 15. Current Limitations

- Redis-backed distributed rate-limit state is not implemented.
- Adaptive limits and counters are process-local and in-memory.
- Adaptive limit changes are global rather than independently maintained per client.
- Live end-to-end validation against a running Python ML service remains pending.
- The Isolation Forest model uses synthetic training data.
- Real-world anomaly detection accuracy has not been established.
- Adaptive thresholds and step sizes require experimental tuning.
- The React monitoring dashboard is not yet implemented.
- Full-system performance comparison remains pending.

## 16. Research Evaluation Plan

The system will be evaluated using controlled traffic scenarios.

### Planned traffic scenarios

1. Normal traffic — regular API request patterns.
2. Sudden traffic spike — a rapid increase in request volume.
3. Anomalous traffic — unusual request behaviour.
4. Recovery phase — traffic returns to normal after an anomalous period.

### Evaluation Metrics

- Legitimate-request rejection rate.
- Anomaly detection precision.
- Anomaly detection recall.
- F1-score, where labelled data is available.
- False-positive rate.
- Throughput.
- p50 and p95 response latency.
- Detection time.
- Adaptation time.

The results will be reported only after the experiments have actually been executed.

## 17. Project Contribution

The intended contribution is an integrated prototype connecting:

1. Conventional API rate-limit enforcement.
2. Traffic trend analysis using EWMA.
3. Machine-learning-based anomaly assessment.
4. Bounded adaptive rate-limit decisions.
5. Controlled comparison against a static rate limiter.

The project also examines practical limitations such as false-positive restrictions, recovery behaviour, global adaptation and process-local state.

## 18. Future Work

- Integrate Redis for shared rate-limit state across backend instances.
- Investigate per-client adaptive limit management.
- Validate the complete Spring Boot and Python ML integration.
- Tune adaptive thresholds using controlled experiments.
- Develop a React monitoring dashboard.
- Perform reproducible static-versus-adaptive performance evaluation.
- Document experimental findings and limitations.

---

**Project:** AI-Driven Adaptive API Rate Limiter  
**Repository:** https://github.com/pragya638/adaptive-rate-limiter
