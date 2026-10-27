"""Unit tests for the Isolation Forest detector and feature calculations."""

from __future__ import annotations

import numpy as np
import pytest
from pydantic import ValidationError

from app.detector import (
    FEATURE_NAMES,
    ZERO_BASELINE_BURST_RATIO,
    AnomalyDetector,
    ModelNotFittedError,
    compute_burst_ratio,
    compute_error_rate,
    compute_request_rate,
)
from app.schemas import TrafficFeatures

from .conftest import NORMAL_SAMPLE, UNUSUAL_SAMPLE


# --------------------------------------------------------------------------- #
# Initialisation and training
# --------------------------------------------------------------------------- #
def test_detector_initialises_with_expected_defaults() -> None:
    detector = AnomalyDetector()

    assert detector.is_fitted is False
    assert detector.feature_names == FEATURE_NAMES
    assert detector.contamination == pytest.approx(0.05)
    assert detector.n_estimators == 200
    assert detector.random_state == 42


@pytest.mark.parametrize(
    "kwargs",
    [
        {"contamination": 0.0},
        {"contamination": 0.9},
        {"n_estimators": 0},
        {"training_samples": 5},
    ],
)
def test_detector_rejects_invalid_configuration(kwargs: dict) -> None:
    with pytest.raises(ValueError):
        AnomalyDetector(**kwargs)


def test_fit_generates_synthetic_data_and_marks_model_fitted() -> None:
    detector = AnomalyDetector(training_samples=300).fit()

    assert detector.is_fitted is True
    assert detector.info()["is_fitted"] is True
    assert detector.info()["training_samples"] == 300


def test_synthetic_data_is_deterministic_and_well_formed() -> None:
    first = AnomalyDetector(random_state=7).generate_synthetic_normal_data(500)
    second = AnomalyDetector(random_state=7).generate_synthetic_normal_data(500)

    assert first.shape == (500, len(FEATURE_NAMES))
    assert np.array_equal(first, second)
    assert np.all(first >= 0.0)
    # error_rate column must stay a valid proportion.
    assert np.all((first[:, 1] >= 0.0) & (first[:, 1] <= 1.0))


def test_fit_accepts_explicit_samples_and_validates_shape() -> None:
    detector = AnomalyDetector()
    detector.fit(np.zeros((12, len(FEATURE_NAMES))))
    assert detector.is_fitted is True

    with pytest.raises(ValueError):
        AnomalyDetector().fit(np.zeros((5, 2)))


def test_fit_rejects_non_finite_samples() -> None:
    samples = np.zeros((5, len(FEATURE_NAMES)))
    samples[2, 0] = np.inf

    with pytest.raises(ValueError):
        AnomalyDetector().fit(samples)


def test_predict_batch_rejects_empty_input(fitted_detector: AnomalyDetector) -> None:
    with pytest.raises(ValueError):
        fitted_detector.predict_batch([])


def test_predict_before_fit_raises() -> None:
    detector = AnomalyDetector()

    with pytest.raises(ModelNotFittedError):
        detector.predict(NORMAL_SAMPLE)


# --------------------------------------------------------------------------- #
# Prediction behaviour
# --------------------------------------------------------------------------- #
def test_normal_traffic_is_scored_as_normal(fitted_detector: AnomalyDetector) -> None:
    result = fitted_detector.predict(NORMAL_SAMPLE)

    assert result.is_anomaly is False
    assert result.label == "normal"
    assert isinstance(result.anomaly_score, float)


def test_unusual_traffic_scores_higher_than_normal(
    fitted_detector: AnomalyDetector,
) -> None:
    normal = fitted_detector.predict(TrafficFeatures(**NORMAL_SAMPLE))
    unusual = fitted_detector.predict(TrafficFeatures(**UNUSUAL_SAMPLE))

    # Direction check: higher anomaly_score means more anomalous.
    assert unusual.anomaly_score > normal.anomaly_score
    # Extremely deviant sample should be flagged (deterministic with fixed seed).
    assert unusual.is_anomaly is True


def test_predictions_are_reproducible_for_a_fixed_seed() -> None:
    first = AnomalyDetector(random_state=123, training_samples=800).fit()
    second = AnomalyDetector(random_state=123, training_samples=800).fit()

    first_result = first.predict(NORMAL_SAMPLE)
    second_result = second.predict(NORMAL_SAMPLE)

    assert first_result.anomaly_score == pytest.approx(second_result.anomaly_score)
    assert first_result.is_anomaly == second_result.is_anomaly


def test_predict_accepts_mapping_and_model_instance(fitted_detector: AnomalyDetector) -> None:
    from_mapping = fitted_detector.predict(NORMAL_SAMPLE)
    from_model = fitted_detector.predict(TrafficFeatures(**NORMAL_SAMPLE))

    assert from_mapping.anomaly_score == pytest.approx(from_model.anomaly_score)
    assert from_mapping.label == from_model.label


def test_predict_batch_returns_one_result_per_sample(fitted_detector: AnomalyDetector) -> None:
    results = fitted_detector.predict_batch([NORMAL_SAMPLE, UNUSUAL_SAMPLE])

    assert len(results) == 2
    assert results[0].anomaly_score < results[1].anomaly_score


def test_info_reports_documented_fields() -> None:
    detector = AnomalyDetector(contamination=0.1, n_estimators=75, random_state=5)
    detector.fit()
    info = detector.info()

    assert info["model"] == "IsolationForest"
    assert info["contamination"] == pytest.approx(0.1)
    assert info["n_estimators"] == 75
    assert info["random_state"] == 5
    assert info["feature_names"] == list(FEATURE_NAMES)
    assert info["is_fitted"] is True


# --------------------------------------------------------------------------- #
# Input validation
# --------------------------------------------------------------------------- #
@pytest.mark.parametrize(
    "payload",
    [
        {**NORMAL_SAMPLE, "request_rate": -1.0},
        {**NORMAL_SAMPLE, "burst_ratio": -0.5},
        {**NORMAL_SAMPLE, "request_count": -3},
    ],
)
def test_negative_values_are_rejected(payload: dict) -> None:
    with pytest.raises(ValidationError):
        TrafficFeatures(**payload)


@pytest.mark.parametrize("error_rate", [-0.01, 1.01, 2.0])
def test_error_rate_outside_unit_range_is_rejected(error_rate: float) -> None:
    with pytest.raises(ValidationError):
        TrafficFeatures(**{**NORMAL_SAMPLE, "error_rate": error_rate})


def test_unknown_extra_fields_are_rejected() -> None:
    with pytest.raises(ValidationError):
        TrafficFeatures(**{**NORMAL_SAMPLE, "unexpected": 1})


@pytest.mark.parametrize("field", ["request_rate", "burst_ratio"])
@pytest.mark.parametrize("value", [float("inf"), float("nan")])
def test_non_finite_float_features_are_rejected(field: str, value: float) -> None:
    with pytest.raises(ValidationError):
        TrafficFeatures(**{**NORMAL_SAMPLE, field: value})


def test_error_rate_non_finite_is_rejected() -> None:
    with pytest.raises(ValidationError):
        TrafficFeatures(**{**NORMAL_SAMPLE, "error_rate": float("inf")})


# --------------------------------------------------------------------------- #
# Feature calculations and zero-baseline handling
# --------------------------------------------------------------------------- #
def test_compute_request_rate_divides_by_interval() -> None:
    assert compute_request_rate(100, 5) == pytest.approx(20.0)


@pytest.mark.parametrize("interval", [0, -1])
def test_compute_request_rate_rejects_non_positive_interval(interval: float) -> None:
    with pytest.raises(ValueError):
        compute_request_rate(10, interval)


def test_compute_request_rate_rejects_negative_count() -> None:
    with pytest.raises(ValueError):
        compute_request_rate(-1, 1)


def test_compute_error_rate_basic_and_zero_total() -> None:
    assert compute_error_rate(5, 100) == pytest.approx(0.05)
    # A window with no requests has a defined error rate of 0.
    assert compute_error_rate(0, 0) == pytest.approx(0.0)


def test_compute_error_rate_rejects_more_errors_than_requests() -> None:
    with pytest.raises(ValueError):
        compute_error_rate(10, 5)


def test_compute_burst_ratio_normal_case() -> None:
    assert compute_burst_ratio(30, 10) == pytest.approx(3.0)


def test_compute_burst_ratio_handles_zero_baseline() -> None:
    # No baseline and no traffic -> 0.0
    assert compute_burst_ratio(0, 0) == pytest.approx(0.0)
    # No baseline but traffic present -> documented clamped maximum
    assert compute_burst_ratio(50, 0) == pytest.approx(ZERO_BASELINE_BURST_RATIO)


def test_compute_burst_ratio_rejects_negative_inputs() -> None:
    with pytest.raises(ValueError):
        compute_burst_ratio(-1, 5)
    with pytest.raises(ValueError):
        compute_burst_ratio(5, -1)
