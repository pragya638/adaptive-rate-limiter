"""Isolation Forest anomaly detection logic.

This module contains **no** FastAPI or HTTP code so that the model can be
trained, tested, and eventually reused by other components. The bridge to the
web layer lives in :mod:`app.main`.

Feature definitions (all computed over one observation interval):

``request_rate``
    ``request_count / interval_seconds`` - requests per second. Must be >= 0.
``error_rate``
    ``error_count / request_count`` - proportion of failed requests in [0, 1].
    When ``request_count`` is 0 the rate is defined as 0.0.
``burst_ratio``
    ``current_rate / baseline_rate`` - how much the current short-window rate
    exceeds the baseline. When the baseline is <= 0 the ratio is undefined; it
    is clamped to ``ZERO_BASELINE_BURST_RATIO`` if there is current traffic, or
    0.0 when there is no traffic at all.
``request_count``
    Raw number of requests observed during the interval. Must be >= 0.

The synthetic training data derives every feature from one observation window
of ``DEFAULT_OBSERVATION_INTERVAL_SECONDS`` seconds. That default matches the
Spring Boot ``monitoring.observation-interval`` (30s) so training and inference
use the same time-window definition. Deployments that change the Java window
must set ``ML_OBSERVATION_INTERVAL_SECONDS`` to the same value; otherwise the
same request count would map to a different request rate in training and
serving. :func:`derive_features` is the single-shot reference implementation of
this contract, and the Java ``MetricsToFeaturesMapper`` mirrors it.
"""

from __future__ import annotations

from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from typing import Any

import numpy as np
from sklearn.ensemble import IsolationForest

from .schemas import LABEL_ANOMALY, LABEL_NORMAL, TrafficFeatures

MODEL_NAME = "IsolationForest"
FEATURE_NAMES: tuple[str, ...] = (
    "request_rate",
    "error_rate",
    "burst_ratio",
    "request_count",
)

# Value used for ``burst_ratio`` when the baseline is zero but traffic exists.
# Any positive traffic with no baseline is an unbounded burst; we clamp to this
# documented maximum so the value stays finite and model-safe.
ZERO_BASELINE_BURST_RATIO = 10.0

# Smallest baseline considered "non-zero" to avoid division blow-ups.
_BASELINE_EPSILON = 1e-9

# Default observation window used to synthesise training data. This MUST match
# the inference window (Spring Boot ``monitoring.observation-interval``, 30s by
# default) so request_count and request_rate keep the same meaning in both
# paths. Override it with an explicit argument / ``ML_OBSERVATION_INTERVAL_SECONDS``.
DEFAULT_OBSERVATION_INTERVAL_SECONDS = 30.0

# Centre of the synthetic *normal* traffic distribution (documented in README).
_NORMAL_RATE_MEAN = 20.0
_NORMAL_RATE_SIGMA = 0.4
_NORMAL_ERROR_BETA = (2.0, 50.0)
# A meaningful share of normal windows are idle or low-traffic. Without these,
# the model treats quiet periods as anomalous and the adaptive limiter can
# ratchet itself down with no real incident.
_NORMAL_IDLE_FRACTION = 0.25
# Fraction of the quiet windows that are fully idle (zero requests). This
# guarantees the model sees the documented zero-count feature semantics.
_NORMAL_FULL_IDLE_SHARE = 0.4
_NORMAL_LOW_RATE_MAX = 5.0


class ModelNotFittedError(RuntimeError):
    """Raised when prediction is attempted before the model is fitted."""


def _require_non_negative(value: float, name: str) -> float:
    if value is None or not np.isfinite(value):
        raise ValueError(f"{name} must be a finite number, got {value!r}")
    if value < 0:
        raise ValueError(f"{name} must be >= 0, got {value!r}")
    return float(value)


def compute_request_rate(request_count: float, interval_seconds: float) -> float:
    """Return requests per second.

    Raises :class:`ValueError` for negative counts or a non-positive interval.
    """
    count = _require_non_negative(request_count, "request_count")
    if interval_seconds is None or not np.isfinite(interval_seconds):
        raise ValueError(f"interval_seconds must be a finite number, got {interval_seconds!r}")
    if interval_seconds <= 0:
        raise ValueError(f"interval_seconds must be > 0, got {interval_seconds!r}")
    return count / float(interval_seconds)


def compute_error_rate(error_count: float, request_count: float) -> float:
    """Return the error proportion in [0, 1].

    A window with zero requests is defined to have an error rate of 0.0.
    Raises :class:`ValueError` for negative counts or ``error_count > request_count``.
    """
    errors = _require_non_negative(error_count, "error_count")
    total = _require_non_negative(request_count, "request_count")
    if total == 0.0:
        return 0.0
    if errors > total:
        raise ValueError(
            f"error_count ({errors}) must not exceed request_count ({total})"
        )
    return errors / total


def compute_burst_ratio(current_rate: float, baseline_rate: float) -> float:
    """Return the burst ratio with safe handling of a zero baseline.

    - ``baseline_rate <= 0`` and ``current_rate == 0`` -> ``0.0``
    - ``baseline_rate <= 0`` and ``current_rate > 0``  -> ``ZERO_BASELINE_BURST_RATIO``
    - otherwise ``current_rate / baseline_rate``
    """
    current = _require_non_negative(current_rate, "current_rate")
    baseline = _require_non_negative(baseline_rate, "baseline_rate")
    if baseline <= _BASELINE_EPSILON:
        return 0.0 if current == 0.0 else ZERO_BASELINE_BURST_RATIO
    return current / baseline


def derive_features(
    *,
    request_count: float,
    error_count: float,
    baseline_rate: float,
    interval_seconds: float = DEFAULT_OBSERVATION_INTERVAL_SECONDS,
) -> TrafficFeatures:
    """Build the four model features from one observation window.

    This is the reference implementation of the training/inference contract:
    ``request_rate`` and ``error_rate`` are derived from the *same*
    ``request_count`` and ``interval_seconds`` that produced ``request_count``,
    and ``burst_ratio`` compares that same rate with ``baseline_rate``.
    """
    count = _require_non_negative(request_count, "request_count")
    if not float(count).is_integer():
        raise ValueError(f"request_count must be an integer, got {request_count!r}")
    request_rate = compute_request_rate(count, interval_seconds)
    error_rate = compute_error_rate(error_count, count)
    burst_ratio = compute_burst_ratio(request_rate, baseline_rate)
    return TrafficFeatures(
        request_rate=request_rate,
        error_rate=error_rate,
        burst_ratio=burst_ratio,
        request_count=int(count),
    )


@dataclass(frozen=True)
class PredictionResult:
    """Outcome of scoring a single traffic sample."""

    is_anomaly: bool
    label: str
    anomaly_score: float


class AnomalyDetector:
    """Thin, reusable wrapper around scikit-learn's :class:`IsolationForest`.

    Parameters
    ----------
    contamination:
        Expected proportion of anomalies. Passed to ``IsolationForest`` and
        used to derive the decision threshold.
    n_estimators:
        Number of isolation trees. More trees -> more stable scores, slower fit.
    random_state:
        Fixed seed for reproducible synthetic data and model fitting.
    training_samples:
        Number of synthetic normal samples generated by :meth:`fit`.
    observation_interval_seconds:
        Length of the observation window used to synthesise training data.
        Must match the inference window so ``request_count`` and
        ``request_rate`` mean the same thing in training and serving.
    """

    def __init__(
        self,
        contamination: float = 0.05,
        n_estimators: int = 200,
        random_state: int = 42,
        training_samples: int = 2000,
        observation_interval_seconds: float = DEFAULT_OBSERVATION_INTERVAL_SECONDS,
    ) -> None:
        if not 0.0 < contamination <= 0.5:
            raise ValueError(f"contamination must be in (0, 0.5], got {contamination}")
        if n_estimators < 1:
            raise ValueError(f"n_estimators must be >= 1, got {n_estimators}")
        if training_samples < 10:
            raise ValueError(f"training_samples must be >= 10, got {training_samples}")
        if not np.isfinite(observation_interval_seconds) or observation_interval_seconds <= 0:
            raise ValueError(
                "observation_interval_seconds must be a finite number > 0, "
                f"got {observation_interval_seconds!r}"
            )

        self._contamination = float(contamination)
        self._n_estimators = int(n_estimators)
        self._random_state = int(random_state)
        self._training_samples = int(training_samples)
        self._observation_interval_seconds = float(observation_interval_seconds)
        self._model = self._build_model()
        self._is_fitted = False
        self._fit_sample_count = 0

    # ------------------------------------------------------------------ #
    # Construction / training
    # ------------------------------------------------------------------ #
    def _build_model(self) -> IsolationForest:
        """Create a fresh, unfitted Isolation Forest."""
        return IsolationForest(
            n_estimators=self._n_estimators,
            contamination=self._contamination,
            max_samples="auto",
            bootstrap=False,
            random_state=self._random_state,
            n_jobs=1,
        )

    def generate_synthetic_normal_data(
        self, n_samples: int | None = None
    ) -> np.ndarray:
        """Generate deterministic *synthetic* normal-traffic samples.

        The data is **not** real production traffic; it is a documented stand-in
        used for development and testing only. Column order follows
        :data:`FEATURE_NAMES`.
        """
        count = self._training_samples if n_samples is None else int(n_samples)
        if count < 1:
            raise ValueError(f"n_samples must be >= 1, got {count}")

        rng = np.random.default_rng(self._random_state)
        interval = self._observation_interval_seconds

        # Target request rate: mostly log-normal around 20 req/s, plus a
        # documented share of idle/low-traffic windows that are also normal.
        busy_rate = rng.lognormal(
            mean=np.log(_NORMAL_RATE_MEAN), sigma=_NORMAL_RATE_SIGMA, size=count
        )
        low_rate = rng.uniform(0.0, _NORMAL_LOW_RATE_MAX, size=count)
        quiet_mask = rng.random(count) < _NORMAL_IDLE_FRACTION
        # Some quiet windows are fully idle so the zero-count rules (zero error
        # rate, zero burst ratio) are represented in normal training data.
        fully_idle_mask = quiet_mask & (rng.random(count) < _NORMAL_FULL_IDLE_SHARE)
        target_rate = np.where(quiet_mask, low_rate, busy_rate)
        target_rate = np.where(fully_idle_mask, 0.0, target_rate)

        # request_count is the primary observation for the configured window.
        # Every other feature is derived from the same interval so training
        # follows the same contract as inference (see :func:`derive_features`).
        request_count = rng.poisson(lam=np.maximum(target_rate * interval, 0.0))
        request_rate = request_count / interval

        # error_rate: error_count / request_count over the same interval. A
        # window with zero requests has an error rate of 0.0.
        error_probability = rng.beta(
            _NORMAL_ERROR_BETA[0], _NORMAL_ERROR_BETA[1], size=count
        )
        error_count = rng.binomial(request_count, error_probability)
        error_rate = np.divide(
            error_count,
            request_count,
            out=np.zeros(count, dtype=float),
            where=request_count > 0,
        )

        # burst_ratio: current request rate / baseline rate. Both rates use the
        # same per-second units as inference. Zero traffic has no baseline to
        # compare against and is defined as 0.0.
        baseline_rate = rng.lognormal(
            mean=np.log(_NORMAL_RATE_MEAN), sigma=_NORMAL_RATE_SIGMA, size=count
        )
        burst_ratio = np.where(
            request_rate <= _BASELINE_EPSILON,
            0.0,
            request_rate / baseline_rate,
        )

        matrix = np.column_stack(
            [request_rate, error_rate, burst_ratio, request_count.astype(float)]
        )
        return np.clip(matrix, 0.0, None)

    def fit(self, samples: np.ndarray | Sequence[Sequence[float]] | None = None) -> "AnomalyDetector":
        """Fit the model.

        When ``samples`` is ``None`` the model trains on synthetic normal
        traffic from :meth:`generate_synthetic_normal_data`.
        """
        matrix = self.generate_synthetic_normal_data() if samples is None else np.asarray(
            samples, dtype=float
        )
        if matrix.ndim != 2 or matrix.shape[1] != len(FEATURE_NAMES):
            raise ValueError(
                f"samples must be a 2D array with {len(FEATURE_NAMES)} columns, "
                f"got shape {matrix.shape}"
            )
        if matrix.shape[0] < 1:
            raise ValueError("samples must contain at least one row")
        if not np.all(np.isfinite(matrix)):
            raise ValueError("samples must contain only finite values")

        self._model = self._build_model()
        self._model.fit(matrix)
        self._is_fitted = True
        self._fit_sample_count = int(matrix.shape[0])
        return self

    # ------------------------------------------------------------------ #
    # Inference
    # ------------------------------------------------------------------ #
    def predict(self, features: TrafficFeatures | Mapping[str, Any]) -> PredictionResult:
        """Score a single sample.

        Accepts a :class:`TrafficFeatures` instance or a mapping with the same
        keys (the mapping is validated through ``TrafficFeatures``).
        """
        validated = self._coerce(features)
        return self._score_vector(np.asarray([validated.to_vector()], dtype=float))[0]

    def predict_batch(
        self, features: Sequence[TrafficFeatures | Mapping[str, Any]]
    ) -> list[PredictionResult]:
        """Score several samples in one call."""
        if not features:
            raise ValueError("features must contain at least one sample")
        rows = [self._coerce(item).to_vector() for item in features]
        matrix = np.asarray(rows, dtype=float)
        return self._score_vector(matrix)

    def _coerce(self, features: TrafficFeatures | Mapping[str, Any]) -> TrafficFeatures:
        if isinstance(features, TrafficFeatures):
            return features
        if isinstance(features, Mapping):
            return TrafficFeatures.model_validate(dict(features))
        raise TypeError(
            "features must be a TrafficFeatures instance or a mapping of feature names"
        )

    def _score_vector(self, matrix: np.ndarray) -> list[PredictionResult]:
        self._ensure_fitted()
        # score_samples: higher => more normal (values are <= 0).
        # predict(): -1 => anomaly, 1 => inlier.
        raw_scores = self._model.score_samples(matrix)
        predictions = self._model.predict(matrix)

        results: list[PredictionResult] = []
        for raw_score, prediction in zip(raw_scores, predictions):
            is_anomaly = bool(prediction == -1)
            # Negate so that a *higher* score means *more anomalous*.
            anomaly_score = float(-raw_score)
            results.append(
                PredictionResult(
                    is_anomaly=is_anomaly,
                    label=LABEL_ANOMALY if is_anomaly else LABEL_NORMAL,
                    anomaly_score=anomaly_score,
                )
            )
        return results

    def _ensure_fitted(self) -> None:
        if not self._is_fitted:
            raise ModelNotFittedError("model is not fitted; call fit() first")

    # ------------------------------------------------------------------ #
    # Introspection
    # ------------------------------------------------------------------ #
    @property
    def is_fitted(self) -> bool:
        return self._is_fitted

    @property
    def feature_names(self) -> tuple[str, ...]:
        return FEATURE_NAMES

    @property
    def contamination(self) -> float:
        return self._contamination

    @property
    def n_estimators(self) -> int:
        return self._n_estimators

    @property
    def random_state(self) -> int:
        return self._random_state

    @property
    def training_samples(self) -> int:
        return self._training_samples

    @property
    def observation_interval_seconds(self) -> float:
        return self._observation_interval_seconds

    def info(self) -> dict[str, Any]:
        """Return non-sensitive model metadata."""
        return {
            "model": MODEL_NAME,
            "contamination": self._contamination,
            "n_estimators": self._n_estimators,
            "random_state": self._random_state,
            "feature_names": list(FEATURE_NAMES),
            "observation_interval_seconds": self._observation_interval_seconds,
            "is_fitted": self._is_fitted,
            "training_samples": self._fit_sample_count,
        }
