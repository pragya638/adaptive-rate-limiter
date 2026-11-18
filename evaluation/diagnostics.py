"""Diagnostics for legitimate rejections, false positives, and limit decreases.

These tables are descriptive. A rejection is paired with the nearest status
poll snapshot to report the observed limit and decision context; the snapshot
is not proof of the causal decision that produced the 429.
"""

from __future__ import annotations

from .metrics import evaluation_rows_from_status, limit_changes
from .traffic import Scenario

REJECTION_FIELDS = [
    "run_id",
    "scenario",
    "config",
    "repetition",
    "request_index",
    "phase",
    "scheduled_offset_seconds",
    "completed_offset_seconds",
    "path",
    "current_limit",
    "decision_action",
    "decision_reason",
    "window_sequence",
    "window_request_rate",
    "window_ewma_request_rate",
    "window_client_error_rate",
]

FALSE_POSITIVE_FIELDS = [
    "run_id",
    "scenario",
    "config",
    "repetition",
    "evaluation_elapsed_seconds",
    "ground_truth",
    "anomaly_score",
    "prediction",
    "current_limit",
    "decision_action",
    "decision_reason",
]

DECREASE_FIELDS = [
    "run_id",
    "scenario",
    "config",
    "repetition",
    "elapsed_seconds",
    "previous_limit",
    "current_limit",
    "delta",
    "decision_action",
    "decision_reason",
    "window_request_rate",
    "window_ewma_request_rate",
    "window_client_error_rate",
]


def _nearest_status(status_rows: list[dict], elapsed: float | None) -> dict | None:
    rows = [
        row
        for row in status_rows
        if row.get("sampled_elapsed_seconds") is not None
    ]
    if not rows or elapsed is None:
        return None
    return min(
        rows,
        key=lambda row: abs(float(row["sampled_elapsed_seconds"]) - float(elapsed)),
    )


def legitimate_rejection_rows(run: dict) -> list[dict]:
    status_rows = run.get("status_rows", [])
    rows: list[dict] = []
    for request in run.get("requests", []):
        if not request.get("legitimate") or request.get("status") != 429:
            continue
        status = _nearest_status(
            status_rows, request.get("completed_offset_seconds")
        ) or {}
        rows.append(
            {
                "run_id": run.get("run_id"),
                "scenario": run.get("scenario"),
                "config": run.get("config"),
                "repetition": run.get("repetition"),
                "request_index": request.get("request_index"),
                "phase": request.get("phase"),
                "scheduled_offset_seconds": request.get("scheduled_offset_seconds"),
                "completed_offset_seconds": request.get("completed_offset_seconds"),
                "path": request.get("path"),
                "current_limit": status.get("current_limit"),
                "decision_action": status.get("decision_action"),
                "decision_reason": status.get("decision_reason"),
                "window_sequence": status.get("window_sequence"),
                "window_request_rate": status.get("window_request_rate"),
                "window_ewma_request_rate": status.get("window_ewma_request_rate"),
                "window_client_error_rate": status.get("window_client_error_rate"),
            }
        )
    return rows


def false_positive_rows(run: dict, scenario: Scenario) -> list[dict]:
    rows: list[dict] = []
    for evaluation in evaluation_rows_from_status(
        run.get("status_rows", []), scenario
    ):
        if not evaluation.get("available") or not evaluation.get("predicted_anomaly"):
            continue
        if evaluation.get("ground_truth") == "anomaly":
            continue
        rows.append(
            {
                "run_id": run.get("run_id"),
                "scenario": run.get("scenario"),
                "config": run.get("config"),
                "repetition": run.get("repetition"),
                "evaluation_elapsed_seconds": evaluation.get(
                    "evaluation_elapsed_seconds"
                ),
                "ground_truth": evaluation.get("ground_truth"),
                "anomaly_score": evaluation.get("anomaly_score"),
                "prediction": evaluation.get("prediction"),
                "current_limit": evaluation.get("current_limit"),
                "decision_action": evaluation.get("decision_action"),
                "decision_reason": evaluation.get("decision_reason"),
            }
        )
    return rows


def limit_decrease_rows(run: dict) -> list[dict]:
    status_rows = run.get("status_rows", [])
    rows: list[dict] = []
    for change in limit_changes(status_rows):
        if change["current_limit"] >= change["previous_limit"]:
            continue
        status = _nearest_status(status_rows, change["elapsed_seconds"]) or {}
        rows.append(
            {
                "run_id": run.get("run_id"),
                "scenario": run.get("scenario"),
                "config": run.get("config"),
                "repetition": run.get("repetition"),
                "elapsed_seconds": change["elapsed_seconds"],
                "previous_limit": change["previous_limit"],
                "current_limit": change["current_limit"],
                "delta": change["current_limit"] - change["previous_limit"],
                "decision_action": status.get("decision_action"),
                "decision_reason": status.get("decision_reason"),
                "window_request_rate": status.get("window_request_rate"),
                "window_ewma_request_rate": status.get("window_ewma_request_rate"),
                "window_client_error_rate": status.get("window_client_error_rate"),
            }
        )
    return rows


def build_diagnostic_tables(
    runs: list[dict], scenarios: dict[str, Scenario]
) -> dict[str, list[dict]]:
    rejections: list[dict] = []
    false_positives: list[dict] = []
    decreases: list[dict] = []
    for run in runs:
        rejections.extend(legitimate_rejection_rows(run))
        decreases.extend(limit_decrease_rows(run))
        scenario = scenarios.get(run.get("scenario"))
        if scenario is not None and run.get("config") == "adaptive":
            false_positives.extend(false_positive_rows(run, scenario))
    rejections.sort(
        key=lambda row: (
            row.get("scenario") or "",
            row.get("config") or "",
            row.get("repetition") or 0,
            row.get("request_index") or 0,
        )
    )
    false_positives.sort(
        key=lambda row: (
            row.get("scenario") or "",
            row.get("config") or "",
            row.get("repetition") or 0,
            row.get("evaluation_elapsed_seconds") or 0.0,
        )
    )
    decreases.sort(
        key=lambda row: (
            row.get("scenario") or "",
            row.get("config") or "",
            row.get("repetition") or 0,
            row.get("elapsed_seconds") or 0.0,
        )
    )
    return {
        "legitimate_rejections": rejections,
        "false_positives": false_positives,
        "limit_decreases": decreases,
    }
