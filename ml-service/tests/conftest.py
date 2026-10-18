"""Shared pytest fixtures for the ml-service test suite."""

from __future__ import annotations

import pytest

from app.detector import AnomalyDetector


@pytest.fixture()
def fitted_detector() -> AnomalyDetector:
    """A detector fitted on the deterministic synthetic normal traffic."""
    detector = AnomalyDetector(
        contamination=0.05, n_estimators=150, random_state=42, training_samples=1500
    )
    return detector.fit()


# A clearly typical window: close to the centre of the synthetic normal data.
NORMAL_SAMPLE = {
    "request_rate": 20.0,
    "error_rate": 0.02,
    "burst_ratio": 1.0,
    "request_count": 20,
}

# A deliberately extreme window: very high rate, almost all errors, huge burst.
UNUSUAL_SAMPLE = {
    "request_rate": 800.0,
    "error_rate": 0.95,
    "burst_ratio": 10.0,
    "request_count": 800,
}
