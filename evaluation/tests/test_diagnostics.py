from __future__ import annotations

from evaluation.diagnostics import (
    build_diagnostic_tables,
    false_positive_rows,
    legitimate_rejection_rows,
    limit_decrease_rows,
)
from evaluation.traffic import build_scenarios


def make_run() -> dict:
    status_fields = {
        "decision_action": "DECREASE",
        "decision_reason": "HIGH_ERROR_RATE",
        "window_request_rate": 8.0,
        "window_ewma_request_rate": 6.0,
        "window_client_error_rate": 0.25,
        "window_sequence": 2,
    }
    return {
        "run_id": "error_heavy_adaptive_r1",
        "scenario": "error_heavy",
        "config": "adaptive",
        "repetition": 1,
        "requests": [
            {
                "request_index": 0,
                "status": 200,
                "legitimate": True,
                "scheduled_offset_seconds": 1.0,
                "completed_offset_seconds": 1.0,
                "path": "/api/demo",
                "phase": "baseline",
            },
            {
                "request_index": 1,
                "status": 429,
                "legitimate": True,
                "scheduled_offset_seconds": 4.6,
                "completed_offset_seconds": 4.6,
                "path": "/api/demo",
                "phase": "abnormal",
            },
        ],
        "status_rows": [
            {
                "sampled_elapsed_seconds": 0.0,
                "current_limit": 5,
                "last_evaluation_at": "2026-01-01T00:00:01Z",
                "last_evaluation_elapsed_seconds": 1.0,
                "anomaly_available": True,
                "anomaly_is_anomaly": True,
                "anomaly_score": 0.4,
                "anomaly_prediction": "anomaly",
                **status_fields,
            },
            {
                "sampled_elapsed_seconds": 4.6,
                "current_limit": 3,
                "last_evaluation_at": "2026-01-01T00:00:05Z",
                "last_evaluation_elapsed_seconds": 5.0,
                "anomaly_available": True,
                "anomaly_is_anomaly": True,
                "anomaly_score": 0.5,
                "anomaly_prediction": "anomaly",
                **status_fields,
            },
        ],
    }


def test_legitimate_rejections_include_nearest_limit_context() -> None:
    rows = legitimate_rejection_rows(make_run())
    assert len(rows) == 1
    assert rows[0]["request_index"] == 1
    assert rows[0]["current_limit"] == 3
    assert rows[0]["decision_reason"] == "HIGH_ERROR_RATE"
    assert rows[0]["phase"] == "abnormal"


def test_false_positives_record_normal_label_at_evaluation_time() -> None:
    scenario = build_scenarios()["error_heavy"]
    rows = false_positive_rows(make_run(), scenario)
    assert len(rows) == 1
    assert rows[0]["evaluation_elapsed_seconds"] == 1.0
    assert rows[0]["ground_truth"] == "normal"
    assert rows[0]["prediction"] == "anomaly"


def test_limit_decreases_report_delta_and_reason() -> None:
    rows = limit_decrease_rows(make_run())
    assert len(rows) == 1
    assert rows[0]["previous_limit"] == 5
    assert rows[0]["current_limit"] == 3
    assert rows[0]["delta"] == -2
    assert rows[0]["decision_reason"] == "HIGH_ERROR_RATE"


def test_build_diagnostic_tables_only_scores_adaptive_false_positives() -> None:
    adaptive = make_run()
    static = {**make_run(), "run_id": "error_heavy_static_r1", "config": "static"}
    tables = build_diagnostic_tables(
        [static, adaptive], build_scenarios()
    )
    assert len(tables["legitimate_rejections"]) == 2
    assert len(tables["false_positives"]) == 1
    assert tables["false_positives"][0]["config"] == "adaptive"
    assert len(tables["limit_decreases"]) == 2
