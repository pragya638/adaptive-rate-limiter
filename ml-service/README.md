# ml-service — Isolation Forest Anomaly Detection (Phase 4)

An independent FastAPI microservice that scores API traffic windows as **normal**
or **anomalous** using a scikit-learn **Isolation Forest** model. It is a standalone
service: it does **not** import Spring Boot, Redis, or any database.

> Phase 4 of the final-year project *"AI-Driven Adaptive API Rate Limiting Using
> Traffic Trend Analysis and Anomaly Detection."* Phase 1 (Spring Boot static
> rate limiter) lives at the repository root and is unchanged by this service.

---

## 1. Purpose

Given four numeric features describing one observation window of API traffic, the
service answers: *"Does this window look anomalous?"* It returns a boolean flag, a
human-readable label, and an unbounded anomaly score.

The service **only detects and reports** anomalies. It does **not** yet make
rate-limit decisions, does not talk to Redis, and does not change the Java
static rate limiter. Those are later phases.

## 2. Architecture and file responsibilities

```
ml-service/
├── app/
│   ├── __init__.py        # package + service version
│   ├── config.py          # Settings (env vars ML_*), validation, safe defaults
│   ├── schemas.py         # Pydantic request/response models (JSON contract)
│   ├── detector.py        # Isolation Forest wrapper + feature calculations (no HTTP)
│   └── main.py            # FastAPI routes, lifespan model fitting, error handling
├── tests/
│   ├── __init__.py
│   ├── conftest.py        # shared fixtures + documented sample payloads
│   ├── test_detector.py   # model + feature-calculation unit tests
│   └── test_api.py        # endpoint, validation and response-structure tests
├── pytest.ini             # testpaths + import path
├── requirements.txt       # pinned dependencies
├── .gitignore             # Python/venv/cache ignores
└── README.md
```

The HTTP layer (`main.py`) is deliberately thin: it validates input, delegates to
`AnomalyDetector`, and shapes the response. All ML logic lives in `detector.py`,
so it can be reused or replaced without touching the routes.

### 2.1 Feature definitions

Every observation covers one window and has four features (fixed order):

| Feature | Calculation | Rules |
|---|---|---|
| `request_rate` | `request_count / interval_seconds` | `>= 0`; interval must be `> 0` |
| `error_rate` | `error_count / request_count` | `0..1`; a window with 0 requests has `error_rate = 0.0` |
| `burst_ratio` | `current_rate / baseline_rate` | `>= 0`; see zero-baseline handling below |
| `request_count` | raw count in the window | integer, `>= 0` |

Zero-baseline handling: if `baseline_rate <= 0`, the ratio is undefined. The helper
`compute_burst_ratio` returns `0.0` when there is also no current traffic, and
clamps to the documented constant `ZERO_BASELINE_BURST_RATIO` (= 10.0) when traffic
exists without a baseline. This keeps values finite and model-safe.

## 3. Prerequisites and virtual environment setup

- Python 3.11+ (this was developed and verified on **Python 3.14.2**; any
  version supported by the pinned wheels works). Check with `python --version`.
- No global packages are required — use a virtual environment.

Windows PowerShell (from the `ml-service` directory):

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

If activation is blocked by execution policy, either run
`Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` first, or skip
activation entirely and call the venv interpreter directly, e.g.
`.\.venv\Scripts\python.exe -m pytest`.

macOS / Linux:

```bash
python3 -m venv .venv
source .venv/bin/activate
```

## 4. Install dependencies

```powershell
python -m pip install --upgrade pip
pip install -r requirements.txt
```

Pinned versions (verified compatible):

```
fastapi==0.143.3
uvicorn==0.54.0
scikit-learn==1.9.1
numpy==2.5.4
pydantic==2.14.1
pytest==9.1.1
httpx==0.28.1
```

(`scikit-learn` brings in `scipy`, `joblib` and `threadpoolctl`; `fastapi`
brings in `starlette`.)

## 5. Run the service locally

With the virtual environment active:

```powershell
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Or directly (uses `ML_HOST` / `ML_PORT`):

```powershell
python -m app.main
```

Interactive docs: <http://127.0.0.1:8000/docs>

### Configuration (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `ML_HOST` | `127.0.0.1` | Bind host |
| `ML_PORT` | `8000` | Bind port |
| `ML_LOG_LEVEL` | `info` | Log level |
| `ML_CONTAMINATION` | `0.05` | Expected anomaly proportion (0.001–0.5) |
| `ML_N_ESTIMATORS` | `200` | Number of isolation trees |
| `ML_RANDOM_STATE` | `42` | Fixed seed (reproducibility) |
| `ML_TRAINING_SAMPLES` | `2000` | Synthetic samples used to fit |

Example (PowerShell):

```powershell
$env:ML_CONTAMINATION = "0.1"; uvicorn app.main:app --port 8000
```

## 6. Run the tests

Windows PowerShell:

```powershell
.\.venv\Scripts\python.exe -m pytest
```

With the venv active: `python -m pytest`. Expected result: all tests pass
(see the final report for the exact count).

## 7. Example `curl` commands (all endpoints)

Windows PowerShell (use `curl.exe` to avoid the PowerShell `curl` alias):

```powershell
# Health
curl.exe http://127.0.0.1:8000/health

# Model info
curl.exe http://127.0.0.1:8000/model/info

# Detect (normal traffic)
curl.exe -X POST http://127.0.0.1:8000/detect -H "Content-Type: application/json" -d "{\"request_rate\":20.0,\"error_rate\":0.02,\"burst_ratio\":1.0,\"request_count\":20}"

# Detect (clearly unusual traffic)
curl.exe -X POST http://127.0.0.1:8000/detect -H "Content-Type: application/json" -d "{\"request_rate\":800.0,\"error_rate\":0.95,\"burst_ratio\":10.0,\"request_count\":800}"

# Invalid payload (missing field) -> HTTP 422
curl.exe -i -X POST http://127.0.0.1:8000/detect -H "Content-Type: application/json" -d "{\"request_rate\":20.0}"
```

Bash/macOS equivalent:

```bash
curl -s -X POST http://127.0.0.1:8000/detect \
  -H "Content-Type: application/json" \
  -d '{"request_rate":20.0,"error_rate":0.02,"burst_ratio":1.0,"request_count":20}'
```

## 8. Example request and response JSON

Request (`POST /detect`):

```json
{
  "request_rate": 20.0,
  "error_rate": 0.02,
  "burst_ratio": 1.0,
  "request_count": 20
}
```

Response:

```json
{
  "is_anomaly": false,
  "prediction": "normal",
  "anomaly_score": 0.38314958734728743,
  "model": "IsolationForest"
}
```

An unusual window and its response:

```json
{ "request_rate": 800.0, "error_rate": 0.95, "burst_ratio": 10.0, "request_count": 800 }
```

```json
{
  "is_anomaly": true,
  "prediction": "anomaly",
  "anomaly_score": 0.7867459831359004,
  "model": "IsolationForest"
}
```

`GET /health`:

```json
{ "status": "ok", "model_ready": true, "model": "IsolationForest", "version": "0.1.0" }
```

`GET /model/info`:

```json
{
  "model": "IsolationForest",
  "contamination": 0.05,
  "n_estimators": 200,
  "random_state": 42,
  "feature_names": ["request_rate", "error_rate", "burst_ratio", "request_count"],
  "is_fitted": true,
  "training_samples": 2000
}
```

Validation errors return HTTP **422** with a structured body:

```json
{
  "error": "validation_error",
  "message": "Request payload failed validation.",
  "details": [ "..." ],
  "path": "/detect"
}
```

Besides negative values and out-of-range `error_rate`, the service explicitly
rejects **non-finite numbers** (`NaN`, `Infinity`, `-Infinity`). This matters
because JSON parsers accept the bare `Infinity`/`NaN` literals, and `+inf` would
otherwise satisfy a `>= 0` lower bound and silently reach the model.

## 9. Isolation Forest and anomaly scores

Isolation Forest builds an ensemble of random binary trees that repeatedly split
the data. Anomalous points are, on average, **isolated in fewer splits** (they sit
in sparse regions), so they have a shorter average path length across the trees.
`scikit-learn` exposes this as:

- `predict()` → `1` for inliers, `-1` for outliers (anomalies).
- `score_samples()` → a raw score (typically `<= 0`) where **higher means more normal**.

This service reports `anomaly_score = -score_samples()`, so **higher means more
anomalous**. The value is an unbounded, unnormalised score — it is **not** a
calibrated probability and must not be read as "percent chance of an anomaly".
The normal/anomaly verdict comes from `predict()`, whose decision threshold is
derived from the configured `contamination` (the expected fraction of anomalies).

## 10. How the synthetic training data is generated

No labelled real dataset was available, so `detector.py` generates a
**deterministic synthetic** "normal traffic" dataset using
`numpy.random.default_rng(random_state)`:

- `request_rate` ~ log-normal around 20 req/s (`sigma = 0.4`) — always positive
- `error_rate` ~ `Beta(2, 50)` (low, within `[0, 1]`)
- `burst_ratio` → absolute value of `Normal(1.0, 0.15)` (centred on no burst)
- `request_count` → Poisson samples consistent with the rate over a 1-second window

All columns are clipped at `0`. Because the seed is fixed, the same seed always
produces identical data and identical model scores.

## 11. Limitations and risks of synthetic data

- **It is not real production traffic.** It is a documented, deterministic
  stand-in for development and testing only.
- The synthetic distribution is a modelling assumption; if real traffic differs
  (different scales, seasonality, diurnal patterns), the model will need
  retraining on real data.
- **No accuracy claims are made.** There is no labelled real dataset, so no
  precision/recall/F1 can be honestly reported. The tests verify behaviour
  (structure, direction, validation, reproducible scores), not real-world accuracy.
- Isolation Forest is unsupervised: `contamination` is a *prior*, not a measured
  truth. Adjust `ML_CONTAMINATION` per deployment.
- No feature scaling is applied (tree-based models do not require it), but feature
  ranges must remain stable between training and serving.

## 12. Future integration with the Spring Boot backend

This service is intentionally decoupled. A later phase can wire it to Spring Boot:

1. The Spring Boot app computes the four features per client/window and `POST`s
   them to `/detect`.
2. Spring Boot reads `is_anomaly` / `anomaly_score` and combines them with the
   EWMA traffic trend to make an adaptive rate-limit decision.
3. The JSON contract above is the stable interface; hosts/ports are configurable
   so no production URLs or secrets are hardcoded here.

Not implemented in this phase, by design: Redis, the adaptive decision engine,
changes to the static rate limiter, database access, and the React dashboard.
