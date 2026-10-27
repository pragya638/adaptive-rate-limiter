"""FastAPI application exposing the Isolation Forest detector over HTTP.

The routes are a thin adapter: all model logic lives in :mod:`app.detector`.
Nothing here connects to Spring Boot, Redis, or any database.
"""

from __future__ import annotations

import logging
import math
from contextlib import asynccontextmanager
from typing import Any, AsyncIterator

from fastapi import FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from . import __version__
from .config import Settings
from .detector import MODEL_NAME, AnomalyDetector, ModelNotFittedError
from .schemas import (
    DetectResponse,
    HealthResponse,
    ModelInfoResponse,
    TrafficFeatures,
)

logger = logging.getLogger("ml_service")

settings = Settings.from_env()

# A single shared detector instance. Isolation Forest inference is read-only,
# so the fitted model can safely serve concurrent requests.
detector = AnomalyDetector(
    contamination=settings.contamination,
    n_estimators=settings.n_estimators,
    random_state=settings.random_state,
    training_samples=settings.training_samples,
)


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    """Fit the model on startup so the service is ready to answer requests."""
    logging.basicConfig(level=settings.log_level.upper())
    detector.fit()
    logger.info(
        "Detector ready: model=%s contamination=%s samples=%s",
        MODEL_NAME,
        detector.contamination,
        detector.training_samples,
    )
    yield


app = FastAPI(
    title="Adaptive Rate Limiter - Anomaly Detection Service",
    description=(
        "Phase 4 service that scores API traffic features with an Isolation "
        "Forest model. Independent of the Spring Boot backend."
    ),
    version=__version__,
    lifespan=lifespan,
)


def _json_safe(value: Any) -> Any:
    """Make validation-error payloads serializable.

    ``NaN``/``Infinity`` are valid Python floats but not valid JSON, and
    Starlette serialises responses with ``allow_nan=False``. Converting them to
    strings keeps the 422 error body intact instead of raising a 500.
    """
    if isinstance(value, float) and not math.isfinite(value):
        return str(value)
    if isinstance(value, dict):
        return {key: _json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_safe(item) for item in value]
    return value


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    """Return a compact, meaningful body for invalid payloads (HTTP 422)."""
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
        content={
            "error": "validation_error",
            "message": "Request payload failed validation.",
            "details": _sanitize_errors(exc.errors()),
            "path": request.url.path,
        },
    )


def _sanitize_errors(errors: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Recursively replace non-finite floats so the body stays JSON-serialisable."""
    return [_json_safe(error) for error in errors]


@app.get("/health", response_model=HealthResponse, tags=["system"])
def health() -> HealthResponse:
    """Liveness probe including model readiness."""
    return HealthResponse(
        status="ok" if detector.is_fitted else "degraded",
        model_ready=detector.is_fitted,
        model=MODEL_NAME,
        version=__version__,
    )


@app.post("/detect", response_model=DetectResponse, tags=["detection"])
def detect(features: TrafficFeatures) -> DetectResponse:
    """Score one window of traffic features and label it normal or anomaly."""
    if not detector.is_fitted:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Model is not ready. Try again shortly.",
        )
    try:
        result = detector.predict(features)
    except ModelNotFittedError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc)
        ) from exc

    return DetectResponse(
        is_anomaly=result.is_anomaly,
        prediction=result.label,  # type: ignore[arg-type]
        anomaly_score=result.anomaly_score,
        model=MODEL_NAME,  # type: ignore[arg-type]
    )


@app.get("/model/info", response_model=ModelInfoResponse, tags=["system"])
def model_info() -> ModelInfoResponse:
    """Return non-sensitive model metadata."""
    info = detector.info()
    return ModelInfoResponse(**info)


if __name__ == "__main__":  # pragma: no cover - convenience entry point
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        log_level=settings.log_level,
        reload=False,
    )
