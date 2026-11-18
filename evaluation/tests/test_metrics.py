from __future__ import annotations

import math
import statistics

import pytest

from evaluation.metrics import (
    adaptation_latency,
    build_repeated_stats,
    compute_detection_metrics,
    detection_latency,
    evaluation_rows_from_status,
    latency_stats,
    limit_changes,
    percentile,
    summarize_requests,
    summary_statistics,
    t_critical_95,
)
from evaluation.traffic import build_scenarios


def request(status: int, latency: float, legitimate: bool) -> dict:
    return {
        "status": status,
        "latency_ms": latency,
        "legitimate": legitimate,
        "completed_offset_seconds": 1.0,
    }


def test_percentile_nearest_rank() -> None:
    assert percentile([1, 2, 3, 4], 50) == 2
    assert percentile([1, 2, 3, 4], 95) == 4
    assert percentile([], 95) is None


def test_latency_stats() -> None:
    stats = latency_stats([10.0, 20.0, 30.0, 40.0])
    assert stats["latency_avg_ms"] == 25.0
    assert stats["latency_p50_ms"] == 20.0
    assert stats["latency_p95_ms"] == 40.0


def test_summarize_requests_uses_limiter_outcomes_and_legitimate_labels() -> None:
    requests = [
        request(200, 10.0, True),
        request(404, 20.0, False),
        request(429, 30.0, True),
        request(200, 40.0, True),
    ]
    summary = summarize_requests(requests, observed_duration_seconds=2.0)
    assert summary["total_requests"] == 4
    assert summary["accepted_requests"] == 3
    assert summary["rejected_requests"] == 1
    assert summary["successful_2xx"] == 2
    assert summary["application_errors_non_429"] == 1
    assert summary["legitimate_requests"] == 3
    assert summary["legitimate_rejected"] == 1
    assert summary["legitimate_rejection_rate"] == pytest.approx(1 / 3)
    assert summary["throughput_rps"] == 2.0
    assert summary["latency_avg_ms"] == 25.0


def test_summarize_requests_handles_no_legitimate_traffic() -> None:
    summary = summarize_requests([request(404, 5.0, False)], 1.0)
    assert summary["legitimate_rejection_rate"] is None
    assert summary["legitimate_requests"] == 0


def test_detection_metrics_counts() -> None:
    rows = [
        {"available": True, "predicted_anomaly": True, "ground_truth": "anomaly"},
        {"available": True, "predicted_anomaly": False, "ground_truth": "anomaly"},
        {"available": True, "predicted_anomaly": True, "ground_truth": "normal"},
        {"available": True, "predicted_anomaly": False, "ground_truth": "normal"},
        {"available": False, "predicted_anomaly": False, "ground_truth": "normal"},
    ]
    metrics = compute_detection_metrics(rows)
    assert metrics["detection_evaluations"] == 4
    assert metrics["true_positives"] == 1
    assert metrics["false_negatives"] == 1
    assert metrics["false_positives"] == 1
    assert metrics["true_negatives"] == 1
    assert metrics["precision"] == 0.5
    assert metrics["recall"] == 0.5
    assert metrics["f1"] == 0.5
    assert metrics["false_positive_rate"] == 0.5


def test_detection_metrics_without_positive_labels() -> None:
    metrics = compute_detection_metrics(
        [{"available": True, "predicted_anomaly": False, "ground_truth": "normal"}]
    )
    assert metrics["precision"] is None
    assert metrics["recall"] is None
    assert metrics["f1"] is None
    assert metrics["false_positive_rate"] == 0.0


def test_limit_changes_are_deduplicated() -> None:
    rows = [
        {"sampled_elapsed_seconds": 0.0, "current_limit": 5},
        {"sampled_elapsed_seconds": 0.1, "current_limit": 5},
        {"sampled_elapsed_seconds": 0.2, "current_limit": 4},
        {"sampled_elapsed_seconds": 0.3, "current_limit": 4},
        {"sampled_elapsed_seconds": 0.4, "current_limit": 3},
    ]
    changes = limit_changes(rows)
    assert [change["current_limit"] for change in changes] == [4, 3]
    assert changes[0]["previous_limit"] == 5


def test_detection_latency_uses_first_true_positive() -> None:
    scenario = build_scenarios()["error_heavy"]
    rows = [
        {
            "available": True,
            "predicted_anomaly": False,
            "evaluation_elapsed_seconds": 4.5,
            "ground_truth": "anomaly",
        },
        {
            "available": True,
            "predicted_anomaly": True,
            "evaluation_elapsed_seconds": 5.5,
            "ground_truth": "anomaly",
        },
    ]
    assert detection_latency(rows, scenario) == pytest.approx(1.5)


def test_adaptation_latency_for_decrease_and_recovery() -> None:
    error_scenario = build_scenarios()["error_heavy"]
    status_rows = [
        {"sampled_elapsed_seconds": 3.0, "current_limit": 5},
        {"sampled_elapsed_seconds": 4.5, "current_limit": 4},
    ]
    assert adaptation_latency(status_rows, error_scenario) == pytest.approx(0.5)

    recovery_scenario = build_scenarios()["recovery"]
    recovery_rows = [
        {"sampled_elapsed_seconds": 5.0, "current_limit": 3},
        {"sampled_elapsed_seconds": 6.0, "current_limit": 2},
        {"sampled_elapsed_seconds": 7.0, "current_limit": 3},
    ]
    assert adaptation_latency(recovery_rows, recovery_scenario) == pytest.approx(1.0)


def test_evaluation_rows_deduplicate_and_skip_pre_experiment() -> None:
    scenario = build_scenarios()["error_heavy"]
    rows = [
        {
            "sampled_elapsed_seconds": -1.0,
            "last_evaluation_at": "2026-01-01T00:00:00Z",
            "last_evaluation_elapsed_seconds": -1.0,
            "anomaly_available": True,
            "anomaly_is_anomaly": False,
        },
        {
            "sampled_elapsed_seconds": 5.0,
            "last_evaluation_at": "2026-01-01T00:00:05Z",
            "last_evaluation_elapsed_seconds": 5.0,
            "anomaly_available": True,
            "anomaly_is_anomaly": True,
        },
        {
            "sampled_elapsed_seconds": 5.1,
            "last_evaluation_at": "2026-01-01T00:00:05Z",
            "last_evaluation_elapsed_seconds": 5.0,
            "anomaly_available": True,
            "anomaly_is_anomaly": True,
        },
    ]
    evaluation_rows = evaluation_rows_from_status(rows, scenario)
    assert len(evaluation_rows) == 1
    assert evaluation_rows[0]["ground_truth"] == "anomaly"


def test_t_critical_95_table_and_fallback() -> None:
    assert t_critical_95(1) == pytest.approx(12.706)
    assert t_critical_95(30) == pytest.approx(2.042)
    assert t_critical_95(100) == pytest.approx(1.96)
    assert t_critical_95(0) is None


def test_summary_statistics_uses_sample_std_and_t_interval() -> None:
    values = [1.0, 2.0, 3.0, 4.0, 5.0]
    stats = summary_statistics(values)
    expected_std = statistics.stdev(values)
    expected_margin = t_critical_95(4) * expected_std / math.sqrt(len(values))
    assert stats["n"] == 5
    assert stats["mean"] == pytest.approx(3.0)
    assert stats["std"] == pytest.approx(expected_std)
    assert stats["ci95_margin"] == pytest.approx(expected_margin)
    assert stats["ci95_low"] == pytest.approx(3.0 - expected_margin)
    assert stats["ci95_high"] == pytest.approx(3.0 + expected_margin)


def test_summary_statistics_handles_missing_and_single_samples() -> None:
    single = summary_statistics([None, float("nan"), 4.0])
    assert single["n"] == 1
    assert single["mean"] == 4.0
    assert single["std"] is None
    assert single["ci95_margin"] is None
    empty = summary_statistics([])
    assert empty["n"] == 0
    assert empty["mean"] is None


def test_build_repeated_stats_groups_and_skips_adaptive_only_metrics() -> None:
    runs = [
        {"scenario": "normal", "config": "static", "legitimate_rejection_rate": 0.0},
        {"scenario": "normal", "config": "static", "legitimate_rejection_rate": 0.2},
        {
            "scenario": "normal",
            "config": "adaptive",
            "legitimate_rejection_rate": 0.1,
            "precision": 1.0,
            "recall": 1.0,
            "f1": 1.0,
        },
    ]
    rows = build_repeated_stats(runs)
    lookup = {(row["scenario"], row["config"], row["metric"]): row for row in rows}
    static_rate = lookup[("normal", "static", "legitimate_rejection_rate")]
    assert static_rate["run_count"] == 2
    assert static_rate["n"] == 2
    assert static_rate["mean"] == pytest.approx(0.1)
    expected_margin = t_critical_95(1) * 0.1414213562373095 / math.sqrt(2)
    assert static_rate["ci95_margin"] == pytest.approx(expected_margin)
    assert ("normal", "static", "anomaly_f1") not in lookup
    assert ("normal", "adaptive", "anomaly_f1") in lookup
