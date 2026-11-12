# AI-Driven Adaptive API Rate Limiter

**Final Year Project | B.Tech Computer Science and Engineering**

An anomaly-aware API rate-limiting system that combines traffic monitoring, EWMA-based trend analysis, and machine-learning-based anomaly assessment to support adaptive request-limit decisions.

## Project Overview

Conventional API rate limiters enforce predefined request limits over a specified time window. Such limits may not respond appropriately to changing traffic patterns, sudden bursts, or potentially anomalous request behaviour.

This project implements an adaptive API rate-limiting prototype using:

- Spring Boot for API request handling and rate-limit enforcement.
- EWMA for traffic trend analysis.
- Python FastAPI and Isolation Forest for anomaly assessment.
- An adaptive decision engine for bounded runtime limit adjustment.
- A dynamic rate limiter for enforcing updated limits.

The project also investigates the trade-off between protecting API availability and avoiding unnecessary rejection of legitimate requests.

## Research Focus

**Research question:** Can combining traffic trend analysis and machine-learning-based anomaly assessment with bounded adaptive rate-limit decisions provide a useful alternative to conventional fixed-window rate limiting under controlled API traffic scenarios?

The project does not claim to invent adaptive rate limiting or the Isolation Forest algorithm. Its focus is the implementation and experimental evaluation of the integrated approach.

## System Architecture

1. API clients send HTTP requests to the Spring Boot application.
2. The rate-limit filter identifies clients and checks the configured request limit.
3. The monitoring service records request metrics.
4. EWMA calculates a smoothed traffic trend.
5. The adaptive scheduler periodically evaluates completed monitoring windows.
6. Traffic features are sent to the Python anomaly detection service.
7. The adaptive decision engine evaluates traffic trends, anomaly assessments and recovery conditions.
8. The adaptive advisor applies bounded limit updates to the dynamic rate limiter.
9. The rate limiter enforces the current limit and returns HTTP 200 or 429 responses as appropriate.

Redis-backed shared state and the React monitoring dashboard are planned extensions.

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.5.x |
| Build | Maven |
| Rate limiting | Fixed-window and dynamic rate limiter |
| Traffic analysis | EWMA |
| Machine learning | Isolation Forest |
| ML API | Python, FastAPI, Pydantic |
| Testing | JUnit, Spring Boot Test, pytest |
| Shared state | Redis — planned |
| Dashboard | React — planned |

## Implementation Status

| Phase | Component | Current status |
|---|---|---|
| 1 | Static fixed-window rate limiter | Implemented |
| 2 | Redis integration | Pending |
| 3 | Traffic monitoring and EWMA | Implemented |
| 4 | Python Isolation Forest service | Implemented independently |
| 5 | Adaptive decision engine and integration | Implemented; live ML validation pending |
| 6 | React monitoring dashboard | Pending |
| 7 | Full-system testing and comparative evaluation | Pending |

## Important Implementation Limitations

- Adaptive limit state and counters are currently process-local.
- The adaptive limit is global rather than independently maintained for each client.
- Redis-backed distributed state is not implemented.
- The ML service uses synthetic training data; real-world anomaly detection performance has not been established.
- Live end-to-end validation using a running Spring Boot application and Python ML service remains pending.
- Adaptive thresholds and decision parameters require further experimental tuning.
- The React monitoring dashboard has not yet been implemented.

## Research Evaluation Plan

The static and adaptive configurations will be evaluated using controlled traffic scenarios:

1. Normal traffic.
2. Sudden burst traffic.
3. Error-heavy traffic.
4. Recovery after an anomalous traffic period.

Evaluation metrics will include:

- Legitimate-request rejection rate.
- Anomaly detection precision, recall and F1-score where labelled data is available.
- False-positive rate.
- Throughput.
- p50 and p95 response latency.
- Anomaly detection and adaptation time.

Results will be reported only after the corresponding experiments have been executed and verified.

## Project Contribution

The intended contribution is an integrated prototype that connects traffic trend monitoring, anomaly assessment and bounded adaptive rate-limit control, together with a controlled comparison against a conventional static rate limiter.

The project also evaluates limitations such as false-positive restrictions, recovery behaviour, global adaptation and process-local state.

## Future Work

- Integrate Redis for shared state across backend instances.
- Improve client-specific adaptation where appropriate.
- Validate the complete Spring Boot and Python ML integration.
- Tune decision thresholds using controlled experiments.
- Implement a React monitoring dashboard.
- Perform reproducible static-versus-adaptive performance evaluation.
