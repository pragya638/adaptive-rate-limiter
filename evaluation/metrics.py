"""Metric calculations for the Phase 7 evaluation."""

from __future__ import annotations

import math
import statistics
from collections import defaultdict
from typing import Iterable

from .traffic import OBSERVATION_INTERVAL_SECONDS, Scenario

# Two-sided 95% Student's t critical values for 1..30 degrees of freedom.
# For larger samples the normal approximation (1.96) is used, which is
# accurate to well under one percent for df > 30.
_T_CRITICAL_975 = {
    1: 12.706,
    2: 4.303,
    3: 3.182,
    4: 2.776,
    5: 2.571,
    6: 2.447,
    7: 2.365,
    8: 2.306,
    9: 2.262,
    10: 2.228,
    11: 2.201,
    12: 2.179,
    13: 2.160,
    14: 2.145,
    15: 2.131,
    16: 2.120,
    17: 2.110,
    18: 2.101,
    19: 2.093,
    20: 2.086,
    21: 2.080,
    22: 2.074,
    23: 2.069,
    24: 2.064,
    25: 2.060,
    26: 2.056,
    27: 2.052,
    28: 2.048,
    29: 2.045,
    30: 2.042,
}


def t_critical_95(degrees_of_freedom: int) -> float | None:
    if degrees_of_freedom < 1:
        return None
    return _T_CRITICAL_975.get(degrees_of_freedom, 1.96)


def summary_statistics(values: Iterable[float | None]) -> dict:
    """Mean, sample standard deviation, and a two-sided 95% t confidence interval.

    Values that are ``None`` or non-finite are excluded and reported through
    ``n``. With fewer than two defined samples, standard deviation and interval
    bounds are ``None`` rather than invented.
    """
    samples = [
        float(value)
        for value in values
        if value is not None and math.isfinite(float(value))
    ]
    count = len(samples)
    if count == 0:
        return {
            "n": 0,
            "mean": None,
            "std": None,
            "ci95_low": None,
            "ci95_high": None,
            "ci95_margin": None,
        }
    mean = sum(samples) / count
    if count < 2:
        return {
            "n": count,
            "mean": mean,
            "std": None,
            "ci95_low": None,
            "ci95_high": None,
            "ci95_margin": None,
        }
    std = statistics.stdev(samples)
    standard_error = std / math.sqrt(count)
    margin = t_critical_95(count - 1) * standard_error
    return {
        "n": count,
        "mean": mean,
        "std": std,
        "ci95_low": mean - margin,
        "ci95_high": mean + margin,
        "ci95_margin": margin,
    }


REPEATED_METRIC_SPECS = (
    ("total_requests", "total_requests", None),
    ("accepted_requests", "accepted_requests", None),
    ("rejected_requests", "rejected_requests", None),
    ("legitimate_rejection_rate", "legitimate_rejection_rate", None),
    ("latency_avg_ms", "latency_avg_ms", None),
    ("latency_p50_ms", "latency_p50_ms", None),
    ("latency_p95_ms", "latency_p95_ms", None),
    ("throughput_rps", "throughput_rps", None),
    ("anomaly_precision", "precision", {"adaptive"}),
    ("anomaly_recall", "recall", {"adaptive"}),
    ("anomaly_f1", "f1", {"adaptive"}),
    ("detection_delay_s", "detection_latency_s", {"adaptive"}),
    ("adaptation_delay_s", "adaptation_latency_s", {"adaptive"}),
    ("limit_min", "limit_min", {"adaptive"}),
    ("limit_max", "limit_max", {"adaptive"}),
)


def build_repeated_stats(run_summaries: list[dict]) -> list[dict]:
    """Summarise per-run metrics across repetitions.

    The unit of analysis is one run. The reported standard deviation is the
    sample standard deviation across runs and the interval is a two-sided 95%
    Student's t interval for the mean.
    """
    groups: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for item in run_summaries:
        groups[(item["scenario"], item["config"])].append(item)

    rows: list[dict] = []
    for (scenario, config), items in sorted(groups.items()):
        for metric_name, source_key, allowed_configs in REPEATED_METRIC_SPECS:
            if allowed_configs is not None and config not in allowed_configs:
                continue
            stats = summary_statistics(item.get(source_key) for item in items)
            rows.append(
                {
                    "scenario": scenario,
                    "config": config,
                    "metric": metric_name,
                    "run_count": len(items),
                    **stats,
                }
            )
    return rows


def percentile(values: Iterable[float], percent: float) -> float | None:
    samples = sorted(float(value) for value in values)
    if not samples:
        return None
    if percent <= 0:
        return samples[0]
    if percent >= 100:
        return samples[-1]
    rank = math.ceil((percent / 100.0) * len(samples))
    index = max(0, min(len(samples) - 1, rank - 1))
    return samples[index]


def latency_stats(values: Iterable[float]) -> dict:
    samples = [float(value) for value in values]
    if not samples:
        return {
            "latency_count": 0,
            "latency_avg_ms": None,
            "latency_p50_ms": None,
            "latency_p95_ms": None,
            "latency_min_ms": None,
            "latency_max_ms": None,
        }
    return {
        "latency_count": len(samples),
        "latency_avg_ms": sum(samples) / len(samples),
        "latency_p50_ms": percentile(samples, 50),
        "latency_p95_ms": percentile(samples, 95),
        "latency_min_ms": min(samples),
        "latency_max_ms": max(samples),
    }


def summarize_requests(requests: list[dict], observed_duration_seconds: float | None = None) -> dict:
    total = len(requests)
    rejected = sum(1 for item in requests if item["status"] == 429)
    transport_errors = sum(1 for item in requests if item["status"] == 0)
    accepted = sum(1 for item in requests if item["status"] > 0 and item["status"] != 429)
    successful = sum(1 for item in requests if 200 <= item["status"] < 300)
    application_errors = sum(
        1 for item in requests if item["status"] >= 400 and item["status"] != 429
    )
    legitimate = [item for item in requests if item.get("legitimate")]
    legitimate_rejected = sum(1 for item in legitimate if item["status"] == 429)
    legitimate_rejection_rate = (
        legitimate_rejected / len(legitimate) if legitimate else None
    )
    failed_legitimate = sum(1 for item in legitimate if item["status"] == 0)
    if observed_duration_seconds is None:
        completed = [item["completed_offset_seconds"] for item in requests]
        observed_duration_seconds = max(completed) if completed else 0.0
    throughput = (
        total / observed_duration_seconds if observed_duration_seconds > 0 else None
    )
    latency = [
        item["latency_ms"]
        for item in requests
        if item["status"] > 0 and item.get("latency_ms") is not None
    ]
    stats = latency_stats(latency)
    return {
        "total_requests": total,
        "accepted_requests": accepted,
        "rejected_requests": rejected,
        "successful_2xx": successful,
        "application_errors_non_429": application_errors,
        "transport_errors": transport_errors,
        "legitimate_requests": len(legitimate),
        "legitimate_rejected": legitimate_rejected,
        "legitimate_rejection_rate": legitimate_rejection_rate,
        "failed_legitimate_requests": failed_legitimate,
        "observed_duration_seconds": observed_duration_seconds,
        "throughput_rps": throughput,
        **stats,
    }


def compute_detection_metrics(rows: list[dict]) -> dict:
    available = [row for row in rows if row.get("available")]
    tp = fp = fn = tn = 0
    for row in available:
        predicted = bool(row.get("predicted_anomaly"))
        actual = row.get("ground_truth") == "anomaly"
        if predicted and actual:
            tp += 1
        elif predicted and not actual:
            fp += 1
        elif not predicted and actual:
            fn += 1
        else:
            tn += 1
    precision = tp / (tp + fp) if (tp + fp) > 0 else None
    recall = tp / (tp + fn) if (tp + fn) > 0 else None
    f1 = (
        2.0 * precision * recall / (precision + recall)
        if precision is not None and recall is not None and (precision + recall) > 0
        else None
    )
    false_positive_rate = fp / (fp + tn) if (fp + tn) > 0 else None
    return {
        "detection_evaluations": len(available),
        "true_positives": tp,
        "false_positives": fp,
        "false_negatives": fn,
        "true_negatives": tn,
        "precision": precision,
        "recall": recall,
        "f1": f1,
        "false_positive_rate": false_positive_rate,
    }


def limit_changes(status_rows: list[dict]) -> list[dict]:
    rows = sorted(
        (row for row in status_rows if row.get("current_limit") is not None),
        key=lambda row: row["sampled_elapsed_seconds"],
    )
    if not rows:
        return []
    previous = rows[0]["current_limit"]
    changes: list[dict] = []
    for row in rows[1:]:
        current = row["current_limit"]
        if current != previous:
            changes.append(
                {
                    "elapsed_seconds": row["sampled_elapsed_seconds"],
                    "previous_limit": previous,
                    "current_limit": current,
                    "sampled_at": row.get("sampled_at"),
                }
            )
            previous = current
    return changes


def limit_summary(status_rows: list[dict]) -> dict:
    rows = sorted(
        (row for row in status_rows if row.get("current_limit") is not None),
        key=lambda row: row["sampled_elapsed_seconds"],
    )
    if not rows:
        return {"limit_min": None, "limit_max": None, "limit_change_count": 0}
    changes = limit_changes(rows)
    limits = [row["current_limit"] for row in rows]
    return {
        "limit_min": min(limits),
        "limit_max": max(limits),
        "limit_change_count": len(changes),
    }


def adaptation_latency(status_rows: list[dict], scenario: Scenario) -> float | None:
    if not scenario.adaptation_phase or not scenario.adaptation_direction:
        return None
    phase = scenario.phase(scenario.adaptation_phase)
    if phase is None:
        return None
    changes = limit_changes(status_rows)
    for change in changes:
        elapsed = change["elapsed_seconds"]
        if elapsed < phase.start_seconds:
            continue
        if elapsed > phase.end_seconds + OBSERVATION_INTERVAL_SECONDS:
            break
        if scenario.adaptation_direction == "decrease":
            if change["current_limit"] < change["previous_limit"]:
                return elapsed - phase.start_seconds
        elif scenario.adaptation_direction == "increase":
            if change["current_limit"] > change["previous_limit"]:
                return elapsed - phase.start_seconds
        else:
            return elapsed - phase.start_seconds
    return None


def detection_latency(evaluation_rows: list[dict], scenario: Scenario) -> float | None:
    if not scenario.detection_phase:
        return None
    phase = scenario.phase(scenario.detection_phase)
    if phase is None:
        return None
    rows = sorted(
        (row for row in evaluation_rows if row.get("available")),
        key=lambda row: row["evaluation_elapsed_seconds"],
    )
    for row in rows:
        if not row.get("predicted_anomaly"):
            continue
        elapsed = row["evaluation_elapsed_seconds"]
        if elapsed < phase.start_seconds:
            continue
        if elapsed > phase.end_seconds + OBSERVATION_INTERVAL_SECONDS:
            break
        if row.get("ground_truth") == "anomaly":
            return elapsed - phase.start_seconds
    return None


def evaluation_rows_from_status(
    status_rows: list[dict], scenario: Scenario
) -> list[dict]:
    rows: list[dict] = []
    seen: set[str] = set()
    for row in sorted(status_rows, key=lambda item: item["sampled_elapsed_seconds"]):
        evaluation_at = row.get("last_evaluation_at")
        if not evaluation_at or evaluation_at in seen:
            continue
        if row.get("anomaly_available") is None:
            continue
        seen.add(evaluation_at)
        elapsed = row.get("last_evaluation_elapsed_seconds")
        if elapsed is None or elapsed < 0:
            continue
        rows.append(
            {
                "evaluation_at": evaluation_at,
                "evaluation_elapsed_seconds": elapsed,
                "sampled_at": row.get("sampled_at"),
                "sampled_elapsed_seconds": row.get("sampled_elapsed_seconds"),
                "available": bool(row.get("anomaly_available")),
                "predicted_anomaly": row.get("anomaly_is_anomaly"),
                "anomaly_score": row.get("anomaly_score"),
                "prediction": row.get("anomaly_prediction"),
                "ground_truth": scenario.ground_truth_at(elapsed),
                "window_sequence": row.get("window_sequence"),
                "window_start": row.get("window_start"),
                "window_end": row.get("window_end"),
                "current_limit": row.get("current_limit"),
                "decision_action": row.get("decision_action"),
                "decision_reason": row.get("decision_reason"),
            }
        )
    return rows
