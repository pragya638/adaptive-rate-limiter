from __future__ import annotations

import csv
import json

from evaluation.report import STATUS_FIELDS, generate_reports
from evaluation.traffic import build_scenarios, phase_summary, trace_hash


def make_request(index: int, status: int, legitimate: bool, ground_truth: str) -> dict:
    return {
        "run_id": "error_heavy_adaptive_r1",
        "scenario": "error_heavy",
        "config": "adaptive",
        "repetition": 1,
        "request_index": index,
        "scheduled_offset_seconds": float(index),
        "sent_offset_seconds": float(index) + 0.01,
        "completed_offset_seconds": float(index) + 0.05,
        "latency_ms": 10.0 + index,
        "status": status,
        "allowed": status != 429,
        "rejected": status == 429,
        "legitimate": legitimate,
        "phase": "abnormal" if ground_truth == "anomaly" else "baseline",
        "ground_truth": ground_truth,
        "path": "/api/missing" if ground_truth == "anomaly" else "/api/demo",
        "error": "",
    }


def make_status_rows(config: str) -> list[dict]:
    rows = [
        {
            "sampled_at": "2026-01-01T00:00:00Z",
            "sampled_elapsed_seconds": 0.0,
            "current_limit": 5,
            "window_sequence": 1,
        },
        {
            "sampled_at": "2026-01-01T00:00:04.6Z",
            "sampled_elapsed_seconds": 4.6,
            "current_limit": 4 if config == "adaptive" else 5,
            "window_sequence": 2,
            "last_evaluation_at": "2026-01-01T00:00:05Z" if config == "adaptive" else None,
            "last_evaluation_elapsed_seconds": 5.0 if config == "adaptive" else None,
            "anomaly_available": True if config == "adaptive" else None,
            "anomaly_is_anomaly": True if config == "adaptive" else None,
            "anomaly_score": 0.4 if config == "adaptive" else None,
            "anomaly_prediction": "anomaly" if config == "adaptive" else None,
            "anomaly_model": "IsolationForest" if config == "adaptive" else None,
        },
        {
            "sampled_at": "2026-01-01T00:00:10.5Z",
            "sampled_elapsed_seconds": 10.5,
            "current_limit": 5,
            "window_sequence": 4,
            "last_evaluation_at": "2026-01-01T00:00:10Z" if config == "adaptive" else None,
            "last_evaluation_elapsed_seconds": 10.0 if config == "adaptive" else None,
            "anomaly_available": True if config == "adaptive" else None,
            "anomaly_is_anomaly": False if config == "adaptive" else None,
            "anomaly_score": -0.1 if config == "adaptive" else None,
            "anomaly_prediction": "normal" if config == "adaptive" else None,
            "anomaly_model": "IsolationForest" if config == "adaptive" else None,
        },
    ]
    for row in rows:
        for field in STATUS_FIELDS:
            row.setdefault(field, None)
    return rows


def make_run(config: str, repetition: int = 1) -> dict:
    trace = [
        {"offset_seconds": float(index), "path": "/api/demo", "legitimate": True, "ground_truth": "normal", "phase": "baseline"}
        for index in range(3)
    ]
    requests = [
        make_request(0, 200, True, "normal"),
        make_request(1, 404, False, "anomaly"),
        make_request(2, 429, True, "normal"),
    ]
    for item in requests:
        item["run_id"] = f"error_heavy_{config}_r{repetition}"
        item["config"] = config
        item["repetition"] = repetition
    return {
        "run_id": f"error_heavy_{config}_r{repetition}",
        "scenario": "error_heavy",
        "config": config,
        "repetition": repetition,
        "trace_hash": trace_hash(trace),
        "trace_length": len(trace),
        "observed_duration_seconds": 2.0,
        "requests": requests,
        "status_rows": make_status_rows(config),
    }


def test_generate_reports_creates_raw_summary_and_charts(tmp_path) -> None:
    scenarios = {"error_heavy": build_scenarios()["error_heavy"]}
    scenario = scenarios["error_heavy"]
    manifest = {
        "generated_at": "2026-01-01T00:00:00Z",
        "seed": 2026,
        "repetitions": 1,
        "static_config": {"adaptive.enabled": "false"},
        "adaptive_config": {"adaptive.enabled": "true"},
        "model_info": {"model": "IsolationForest", "observation_interval_seconds": 2.0},
        "scenarios": [
            {
                "name": scenario.name,
                "description": scenario.description,
                "phases": phase_summary(scenario),
            }
        ],
    }
    generate_reports(
        tmp_path,
        manifest,
        [make_run("static"), make_run("adaptive")],
        scenarios,
    )
    for name in (
        "summary_runs.csv",
        "summary_aggregate.csv",
        "detection_metrics.csv",
        "results.json",
        "manifest.json",
        "comparison.md",
        "charts/legitimate_rejection_rate.svg",
        "charts/latency_p95.svg",
        "charts/throughput.svg",
        "charts/limit_timeline_error_heavy.svg",
    ):
        assert (tmp_path / name).exists(), name
    comparison = (tmp_path / "comparison.md").read_text(encoding="utf-8")
    assert "Phase 7 Evaluation Results" in comparison
    assert "error_heavy" in comparison
    assert "These experiments use synthetic labelled traffic" in comparison
    svg = (tmp_path / "charts" / "latency_p95.svg").read_text(encoding="utf-8")
    assert svg.startswith("<svg")
    payload = json.loads((tmp_path / "results.json").read_text(encoding="utf-8"))
    assert "aggregates" in payload
    assert "NaN" not in json.dumps(payload)
    with (tmp_path / "summary_aggregate.csv").open(encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    assert len(rows) == 2
    assert {row["config"] for row in rows} == {"static", "adaptive"}


def test_summary_rows_include_expected_request_fields(tmp_path) -> None:
    scenarios = {"error_heavy": build_scenarios()["error_heavy"]}
    manifest = {
        "generated_at": "2026-01-01T00:00:00Z",
        "seed": 2026,
        "repetitions": 1,
        "static_config": {},
        "adaptive_config": {},
        "model_info": {},
        "scenarios": [
            {
                "name": "error_heavy",
                "description": "test",
                "phases": phase_summary(scenarios["error_heavy"]),
            }
        ],
    }
    generate_reports(tmp_path, manifest, [make_run("static")], scenarios)
    with (tmp_path / "summary_runs.csv").open(encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        row = next(reader)
    assert row["scenario"] == "error_heavy"
    assert row["config"] == "static"
    assert row["total_requests"] == "3"
    assert row["rejected_requests"] == "1"
    assert row["legitimate_rejection_rate"] == "0.500000"
    assert row["latency_p50_ms"] == "11.000000"
    assert "trace_hash" in row
    assert "detection_evaluations" in row


def test_repeated_reports_include_statistics_diagnostics_and_ci(tmp_path) -> None:
    scenarios = {"error_heavy": build_scenarios()["error_heavy"]}
    manifest = {
        "generated_at": "2026-01-01T00:00:00Z",
        "seed": 2026,
        "repetitions": 2,
        "static_config": {},
        "adaptive_config": {},
        "model_info": {},
        "scenarios": [
            {
                "name": "error_heavy",
                "description": "test",
                "phases": phase_summary(scenarios["error_heavy"]),
            }
        ],
    }
    runs = [
        make_run(config, repetition)
        for repetition in (1, 2)
        for config in ("static", "adaptive")
    ]
    generate_reports(tmp_path, manifest, runs, scenarios)
    for name in (
        "summary_repeated.csv",
        "diagnostics_legitimate_rejections.csv",
        "diagnostics_false_positives.csv",
        "diagnostics_limit_decreases.csv",
        "diagnostics_summary.csv",
        "charts/legitimate_rejection_rate_ci.svg",
        "charts/latency_p95_ci.svg",
        "charts/throughput_ci.svg",
    ):
        assert (tmp_path / name).exists(), name
    with (tmp_path / "summary_repeated.csv").open(encoding="utf-8") as handle:
        repeated_rows = list(csv.DictReader(handle))
    assert repeated_rows
    assert all(row["run_count"] == "2" for row in repeated_rows)
    payload = json.loads((tmp_path / "results.json").read_text(encoding="utf-8"))
    assert payload["repeated_stats"]
    assert "legitimate_rejections" in payload["diagnostics"]
    comparison = (tmp_path / "comparison.md").read_text(encoding="utf-8")
    assert "Repeated-Run Reliability (95% CI)" in comparison
    assert "Failure Diagnostics" in comparison
    svg = (tmp_path / "charts" / "latency_p95_ci.svg").read_text(encoding="utf-8")
    assert "stroke-width=\"1.5\"" in svg
