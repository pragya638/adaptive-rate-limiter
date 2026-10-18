"""Pydantic request/response models (the JSON contract of the service).

The models are intentionally independent of Spring Boot and Redis. They define
a stable wire format that the Java backend can adopt later.
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

# Human-readable labels returned by the detector.
LABEL_NORMAL = "normal"
LABEL_ANOMALY = "anomaly"
PredictionLabel = Literal["normal", "anomaly"]


class TrafficFeatures(BaseModel):
    """Numeric API traffic features for a single observation window.

    All fields are required. Negative values, out-of-range proportions, and
    non-finite numbers (``NaN``/``Infinity``) are rejected with HTTP 422 by
    FastAPI. ``allow_inf_nan=False`` is set explicitly because JSON parsers may
    accept the bare ``Infinity``/``NaN`` literals and ``+inf`` would otherwise
    satisfy the lower bounds.
    """

    model_config = ConfigDict(extra="forbid")

    request_rate: float = Field(
        ...,
        ge=0.0,
        allow_inf_nan=False,
        description="Requests per second observed over the interval (>= 0).",
        examples=[20.0],
    )
    error_rate: float = Field(
        ...,
        ge=0.0,
        le=1.0,
        allow_inf_nan=False,
        description="Proportion of requests that resulted in errors, in [0, 1].",
        examples=[0.02],
    )
    burst_ratio: float = Field(
        ...,
        ge=0.0,
        allow_inf_nan=False,
        description=(
            "Current short-window request rate divided by a baseline rate. "
            "Zero baselines are handled during feature calculation (>= 0)."
        ),
        examples=[1.0],
    )
    request_count: int = Field(
        ...,
        ge=0,
        description="Total requests observed during the interval (>= 0).",
        examples=[20],
    )

    def to_vector(self) -> list[float]:
        """Return the feature vector in the canonical ``FEATURE_NAMES`` order."""
        return [
            float(self.request_rate),
            float(self.error_rate),
            float(self.burst_ratio),
            float(self.request_count),
        ]


class DetectResponse(BaseModel):
    """Result of a single anomaly detection request."""

    is_anomaly: bool = Field(..., description="True when the sample is flagged as anomalous.")
    prediction: PredictionLabel = Field(..., description="Human-readable label.")
    anomaly_score: float = Field(
        ...,
        description=(
            "Unbounded anomaly score where higher means more anomalous. "
            "This is NOT a calibrated probability."
        ),
    )
    model: Literal["IsolationForest"] = Field(
        default="IsolationForest", description="Model that produced the prediction."
    )


class HealthResponse(BaseModel):
    """Service liveness and model readiness."""

    status: Literal["ok", "degraded"] = Field(..., description="Overall service status.")
    model_ready: bool = Field(..., description="True when the model has been fitted.")
    model: str = Field(..., description="Detector implementation name.")
    version: str = Field(..., description="Service version.")


class ModelInfoResponse(BaseModel):
    """Non-sensitive model metadata for debugging and integration checks."""

    model: str
    contamination: float
    n_estimators: int
    random_state: int
    feature_names: list[str]
    is_fitted: bool
    training_samples: int


class ErrorResponse(BaseModel):
    """Uniform error payload returned for validation and runtime failures."""

    error: str
    message: str
