# Verification Report — Redis Integration and Release Validation

Date: 2026-11-18
Branch under preparation: `feature/complete-adaptive-rate-limiter`
Project: **AI-Driven Adaptive API Rate Limiter**

## Scope

This report records verification of the opt-in Redis integration, the full-stack functional behavior and the latest automated test runs for the release PR.

No application algorithms, thresholds, API contracts or Redis behavior were changed for Git preparation. The default in-memory mode remains the default; Redis mode is opt-in.

## Latest Test Results (2026-11-18)

These results supersede the earlier Phase 4A counts listed below.

| Suite | Command/scope | Result |
|-------|---------------|--------|
| Java (Redis available) | Full Maven suite with local Redis on `6380` | **35 suites, 187 tests, 0 failures, 0 errors, 0 skipped** |
| Java (Redis unavailable) | Full Maven suite without Redis | **35 suites, 187 tests, 177 passed, 0 failures, 0 errors, 10 skipped** |
| Focused Redis integration | Redis-only rerun against local Redis on `6380` | **26 tests, 0 failures, 0 errors, 0 skipped** |
| Python ML service | `python -m pytest -q` in `ml-service/` | **76 passed** (1 third-party deprecation warning) |
| Frontend tests | `npm test` in `frontend/` | **7 files, 23 tests passed** |
| Frontend typecheck | `npm run typecheck` in `frontend/` | Passed |
| Frontend build | `npm run build` in `frontend/` | Passed |
| Evaluation framework | `pytest evaluation/tests -q` | **37 passed** |

The Java “without Redis” run discovered the same `187` tests as the Redis-enabled run; `10` Redis-dependent tests skipped when Redis was unavailable. The Redis-enabled run executed all `187` tests. The focused Redis-only rerun reported `26 passed`.

## Earlier Results (2026-11-14, retained for traceability)

| Suite | Result |
|-------|--------|
| Java with local Redis | **144 tests, 0 failures, 0 errors, 0 skipped** (28 suites) |
| Java without Redis | **134 passed, 0 failures, 0 errors, 10 skipped** |
| Redis integration only | **10 tests, 0 failures, 0 errors, 0 skipped** |
| Python ML service | **76 passed** |
| Evaluation framework | **37 passed** |

Do not add earlier and later counts together; they are separate runs from different project phases.

## Redis Coverage

Automated integration tests verify:

- Atomic shared counter increments across limiter instances.
- TTL expiry aligned with the fixed window plus grace period.
- Reset/delete behavior for prefixed counter keys.
- Shared adaptive limit visible to multiple limiter instances.
- Leader lock acquisition, contention, TTL expiry and safe release.
- Fail-open local fallback and fail-closed rejection behavior.
- Last-known adaptive limit retention when Redis is unavailable.
- Conditional bean selection for `memory` vs `redis`.

## Redis Live Demo (2026-11-18)

A two-instance live demo was run with local Redis `localhost:6380` and shared prefix `rl-demo-20261118`.

- Backend `8080` ran in Redis mode.
- A temporary backend `8081` ran with the same Redis configuration and key prefix.
- Both instances reported:
  - `store=REDIS`
  - `status=ready`
  - detail `Redis PING succeeded.`
  - fallback `OPEN`
- A single client alternated requests between `8080` and `8081`.
- The shared fixed-window counter allowed `4` requests and rejected `6` with HTTP `429`.
- The adaptive limit changed from `5→4` during the burst due to `TRAFFIC_SPIKE`; this was expected.
- Both instances later reported the same current limit (`3`), confirming shared adaptive-limit storage.
- The dashboard DOM/screenshot showed `Redis store`, `Redis PING succeeded.`, `Store configuration REDIS`, and no Redis-backed history persistence claim.
- The temporary `8081` backend was stopped after the proof.

Evidence logs and screenshots are kept in a local temporary evidence directory and are intentionally not staged in the repository.

## Full-Stack Functional Verification (2026-11-18)

- All services reachable: backend `8080`, frontend `5173`, ML `8000` → HTTP `200`.
- Six dashboard pages rendered: Overview, Rate Limiting, Traffic Monitoring, Anomaly Detection, Adaptive Decisions, System Status.
- Rate limiting verified:
  - At limit `5`, `5` requests allowed then HTTP `429`.
  - At adaptive limit `4`, `4` requests allowed then HTTP `429`.
- EWMA verified:
  - `119` consecutive observation pairs.
  - α=`0.3`.
  - Maximum recurrence error `1.11022302462516E-16`.
  - Metrics and status agreed with the latest history sample.
- ML verified:
  - `/health`, `/model/info`, `/detect` responded.
  - Normal score: `0.5633162533577502`.
  - Extreme anomaly score: `0.82350943484316`.
  - Invalid payload returned HTTP `422`.
  - Backend anomaly vector mirrored direct `/detect` with score difference `0`.
- Adaptive decisions:
  - `120` observations.
  - `4 DECREASE_LIMIT`, `116 KEEP_LIMIT`.
  - Observed path `5→4→3→2→1`.
- CSV exports:
  - Traffic, anomaly, decision and correlation CSVs included BOM and correct headers.
  - Each had `120` data rows.

## Environment Notes

- OS: Windows.
- Java 17; Spring Boot 3.5.16.
- Redis integration tests prefer Testcontainers when Docker is available.
- Docker Desktop was unavailable, so local Redis 5.0.14.1 on `localhost:6380` was used via `REDIS_HOST`/`REDIS_PORT`.
- When neither Docker nor Redis is available, Redis integration tests skip rather than fail.
- Python ML service ran on port `8000`; React dashboard ran on port `5173`.

## Known Limitations

- `rate-limit.store=memory` remains the default; Redis mode is opt-in.
- Dashboard history is bounded in memory and resets on backend restart; it is not durable Redis-backed history.
- Adaptive evaluation uses the leader instance's local monitoring snapshot. Cluster-wide metric aggregation is **not** implemented.
- During fail-open Redis outages, counters fall back to per-instance state; distributed guarantees do not apply while degraded.
- No dedicated Redis health endpoint is exposed; `GET /api/system-status` reports store status.
- The `X-Client-Id` header is not authenticated, and in-memory client state is not evicted.
- With `monitoring.observation-interval=2s` and `dashboard-history.max-records=120`, retained history spans about four minutes; a minimum five-minute UI filter cannot exclude older rows in this configuration.
- The Vite dev proxy may return HTTP `500` when the backend is stopped, producing a less precise UI error than a direct network-unreachable failure.
- Docker-based Testcontainers verification was not executed because the Docker daemon was unavailable.
- The ML model is trained on synthetic data; false positives are possible.
- Evaluation traces are synthetic/open-loop only, not production traffic or real incidents.
