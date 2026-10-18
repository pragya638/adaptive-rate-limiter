"""API tests for the FastAPI anomaly detection service.

These tests exercise real request/response behaviour through the ASGI app via
HTTPX-backed ``TestClient``; they assert on structure, validation, and sensible
model behaviour rather than only HTTP status codes.
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.main import app, detector

from .conftest import NORMAL_SAMPLE, UNUSUAL_SAMPLE


@pytest.fixture(scope="module")
def client() -> "TestClient":
    # Using the context manager triggers the lifespan, fitting the model.
    with TestClient(app) as test_client:
        yield test_client


def test_health_reports_service_and_model_readiness(client) -> None:
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert response.headers["content-type"].startswith("application/json")
    assert body["status"] == "ok"
    assert body["model_ready"] is True
    assert body["model"] == "IsolationForest"
    assert isinstance(body["version"], str) and body["version"]


# --------------------------------------------------------------------------- #
# Detection
# --------------------------------------------------------------------------- #
def test_detect_returns_documented_structure_for_normal_traffic(client) -> None:
    response = client.post("/detect", json=NORMAL_SAMPLE)

    assert response.status_code == 200
    body = response.json()
    assert set(body) == {"is_anomaly", "prediction", "anomaly_score", "model"}
    assert body["model"] == "IsolationForest"
    assert isinstance(body["is_anomaly"], bool)
    assert body["prediction"] in {"normal", "anomaly"}
    assert isinstance(body["anomaly_score"], float)
    # A typical window should be labelled normal.
    assert body["prediction"] == "normal"


def test_detect_scores_unusual_traffic_more_anomalous(client) -> None:
    normal = client.post("/detect", json=NORMAL_SAMPLE).json()
    unusual = client.post("/detect", json=UNUSUAL_SAMPLE).json()

    assert unusual["anomaly_score"] > normal["anomaly_score"]
    assert unusual["prediction"] == "anomaly"
    assert unusual["is_anomaly"] is True


# --------------------------------------------------------------------------- #
# Invalid payloads
# --------------------------------------------------------------------------- #
@pytest.mark.parametrize(
    "payload",
    [
        {**NORMAL_SAMPLE, "request_rate": -5.0},
        {**NORMAL_SAMPLE, "burst_ratio": -1.0},
        {**NORMAL_SAMPLE, "request_count": -1},
    ],
)
def test_detect_rejects_negative_values(client, payload: dict) -> None:
    response = client.post("/detect", json=payload)

    assert response.status_code == 422
    body = response.json()
    assert body["error"] == "validation_error"
    assert body["path"] == "/detect"


@pytest.mark.parametrize("error_rate", [-0.1, 1.5])
def test_detect_rejects_error_rate_outside_range(client, error_rate: float) -> None:
    response = client.post("/detect", json={**NORMAL_SAMPLE, "error_rate": error_rate})

    assert response.status_code == 422
    assert response.json()["error"] == "validation_error"


def test_detect_rejects_missing_fields(client) -> None:
    response = client.post("/detect", json={"request_rate": 20.0})

    assert response.status_code == 422


def test_detect_rejects_non_numeric_values(client) -> None:
    response = client.post(
        "/detect", json={**NORMAL_SAMPLE, "request_rate": "fast"}
    )

    assert response.status_code == 422


def test_detect_rejects_unknown_extra_fields(client) -> None:
    response = client.post("/detect", json={**NORMAL_SAMPLE, "extra": 1})

    assert response.status_code == 422


def test_detect_rejects_malformed_json(client) -> None:
    response = client.post(
        "/detect", content="{not json", headers={"Content-Type": "application/json"}
    )

    assert response.status_code == 422


def test_detect_rejects_wrong_http_method(client) -> None:
    response = client.get("/detect")

    assert response.status_code == 405


@pytest.mark.parametrize(
    "field", ["request_rate", "burst_ratio", "error_rate", "request_count"]
)
@pytest.mark.parametrize("token", ["Infinity", "-Infinity", "NaN"])
def test_detect_rejects_non_finite_numbers(client, field: str, token: str) -> None:
    # JSON parsers accept the bare Infinity/NaN literals, so build the body manually.
    base = {
        "request_rate": "20.0",
        "error_rate": "0.02",
        "burst_ratio": "1.0",
        "request_count": "20",
    }
    base[field] = token
    raw = "{" + ",".join(f'"{key}":{value}' for key, value in base.items()) + "}"

    response = client.post(
        "/detect", content=raw, headers={"Content-Type": "application/json"}
    )

    assert response.status_code == 422
    assert response.json()["error"] == "validation_error"


# --------------------------------------------------------------------------- #
# Model info
# --------------------------------------------------------------------------- #
def test_model_info_reports_expected_fields(client) -> None:
    response = client.get("/model/info")

    assert response.status_code == 200
    body = response.json()
    assert body["model"] == "IsolationForest"
    assert 0.0 < body["contamination"] <= 0.5
    assert isinstance(body["n_estimators"], int)
    assert body["feature_names"] == [
        "request_rate",
        "error_rate",
        "burst_ratio",
        "request_count",
    ]
    assert body["is_fitted"] is True
    assert body["training_samples"] > 0


# --------------------------------------------------------------------------- #
# Docs, model readiness and failure handling
# --------------------------------------------------------------------------- #
def test_openapi_and_docs_are_available(client) -> None:
    assert client.get("/openapi.json").status_code == 200
    assert client.get("/docs").status_code == 200


def test_health_reports_degraded_when_model_not_fitted(client, monkeypatch) -> None:
    monkeypatch.setattr(detector, "_is_fitted", False)

    body = client.get("/health").json()

    assert body["status"] == "degraded"
    assert body["model_ready"] is False


def test_detect_returns_503_when_model_not_fitted(client, monkeypatch) -> None:
    monkeypatch.setattr(detector, "_is_fitted", False)

    response = client.post("/detect", json=NORMAL_SAMPLE)

    assert response.status_code == 503


def test_detect_does_not_mask_unexpected_errors(client, monkeypatch) -> None:
    def boom(_features):
        raise RuntimeError("simulated internal failure")

    monkeypatch.setattr(detector, "predict", boom)

    # raise_server_exceptions=False lets us observe the 500 instead of an exception.
    with TestClient(app, raise_server_exceptions=False) as raw_client:
        response = raw_client.post("/detect", json=NORMAL_SAMPLE)

    assert response.status_code == 500
    # The failure must not be silently converted into a normal prediction.
    assert "prediction" not in response.text
    assert "is_anomaly" not in response.text


# --------------------------------------------------------------------------- #
# Model info
# --------------------------------------------------------------------------- #
def test_model_info_reports_expected_fields(client) -> None:
    response = client.get("/model/info")

    assert response.status_code == 200
    body = response.json()
    assert body["model"] == "IsolationForest"
    assert 0.0 < body["contamination"] <= 0.5
    assert isinstance(body["n_estimators"], int)
    assert body["feature_names"] == [
        "request_rate",
        "error_rate",
        "burst_ratio",
        "request_count",
    ]
    assert body["is_fitted"] is True
    assert body["training_samples"] > 0
