"""Result aggregation, CSV/JSON output, tables, and SVG charts."""

from __future__ import annotations

import csv
import json
import math
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from .charts import bar_chart_svg, line_chart_svg
from .diagnostics import (
    DECREASE_FIELDS,
    FALSE_POSITIVE_FIELDS,
    REJECTION_FIELDS,
    build_diagnostic_tables,
)
from .metrics import (
    adaptation_latency,
    build_repeated_stats,
    compute_detection_metrics,
    detection_latency,
    evaluation_rows_from_status,
    limit_summary,
    summarize_requests,
)
from .traffic import Scenario

SUMMARY_FIELDS = [
    "row_type",
    "run_id",
    "scenario",
    "config",
    "repetition",
    "run_count",
    "trace_hash",
    "trace_length",
    "total_requests",
    "accepted_requests",
    "rejected_requests",
    "successful_2xx",
    "application_errors_non_429",
    "transport_errors",
    "legitimate_requests",
    "legitimate_rejected",
    "legitimate_rejection_rate",
    "observed_duration_seconds",
    "throughput_rps",
    "latency_count",
    "latency_avg_ms",
    "latency_p50_ms",
    "latency_p95_ms",
    "latency_min_ms",
    "latency_max_ms",
    "detection_evaluations",
    "true_positives",
    "false_positives",
    "false_negatives",
    "true_negatives",
    "precision",
    "recall",
    "f1",
    "false_positive_rate",
    "detection_latency_s",
    "detection_latency_samples",
    "adaptation_latency_s",
    "adaptation_latency_samples",
    "limit_min",
    "limit_max",
    "limit_change_count",
]

DETECTION_FIELDS = [
    "row_type",
    "scenario",
    "config",
    "repetition",
    "run_count",
    "detection_evaluations",
    "true_positives",
    "false_positives",
    "false_negatives",
    "true_negatives",
    "precision",
    "recall",
    "f1",
    "false_positive_rate",
    "detection_latency_s",
    "detection_latency_samples",
]

REPEATED_FIELDS = [
    "scenario",
    "config",
    "metric",
    "run_count",
    "n",
    "mean",
    "std",
    "ci95_low",
    "ci95_high",
    "ci95_margin",
]

DIAGNOSTIC_SUMMARY_FIELDS = [
    "scenario",
    "config",
    "legitimate_rejections",
    "false_positives",
    "limit_decreases",
]

REQUEST_FIELDS = [
    "run_id",
    "scenario",
    "config",
    "repetition",
    "request_index",
    "scheduled_offset_seconds",
    "sent_offset_seconds",
    "completed_offset_seconds",
    "latency_ms",
    "status",
    "allowed",
    "rejected",
    "legitimate",
    "phase",
    "ground_truth",
    "path",
    "error",
]

STATUS_FIELDS = [
    "run_id",
    "scenario",
    "config",
    "repetition",
    "sampled_at",
    "sampled_elapsed_seconds",
    "enabled",
    "mode",
    "current_limit",
    "window_sequence",
    "window_start",
    "window_end",
    "window_duration_seconds",
    "window_request_count",
    "window_allowed_requests",
    "window_rejected_requests",
    "window_client_error_rate",
    "window_request_rate",
    "window_ewma_request_rate",
    "last_evaluation_at",
    "last_evaluation_elapsed_seconds",
    "last_adaptation_at",
    "last_adaptation_elapsed_seconds",
    "anomaly_available",
    "anomaly_is_anomaly",
    "anomaly_score",
    "anomaly_prediction",
    "anomaly_model",
    "decision_action",
    "decision_previous_limit",
    "decision_applied_limit",
    "decision_reason",
    "decision_evaluated_at",
]


def _clean_float(value):
    if isinstance(value, float) and not math.isfinite(value):
        return None
    return value


def json_safe(value):
    if isinstance(value, dict):
        return {str(key): json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [json_safe(item) for item in value]
    if isinstance(value, Path):
        return str(value)
    if isinstance(value, float):
        return _clean_float(value)
    return value


def write_json(path: Path, payload) -> None:
    path.write_text(
        json.dumps(json_safe(payload), indent=2, allow_nan=False, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def write_csv(path: Path, rows: list[dict], fieldnames: list[str]) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    field: _csv_value(row.get(field))
                    for field in fieldnames
                }
            )


def _csv_value(value):
    if value is None:
        return ""
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, float):
        if not math.isfinite(value):
            return ""
        return f"{value:.6f}"
    return value


def build_run_summary(run: dict, scenario: Scenario) -> dict:
    requests = run["requests"]
    summary = summarize_requests(requests, run.get("observed_duration_seconds"))
    evaluations = evaluation_rows_from_status(run["status_rows"], scenario)
    detection = compute_detection_metrics(evaluations)
    detection_s = detection_latency(evaluations, scenario)
    adaptation_s = adaptation_latency(run["status_rows"], scenario)
    limits = limit_summary(run["status_rows"])
    return {
        **summary,
        **detection,
        "detection_latency_s": detection_s,
        "detection_latency_samples": 1 if detection_s is not None else 0,
        "adaptation_latency_s": adaptation_s,
        "adaptation_latency_samples": 1 if adaptation_s is not None else 0,
        **limits,
        "evaluation_rows": evaluations,
    }


def _mean(values: list[float]) -> float | None:
    return sum(values) / len(values) if values else None


def build_aggregates(run_summaries: list[dict]) -> list[dict]:
    groups: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for item in run_summaries:
        groups[(item["scenario"], item["config"])].append(item)
    aggregates: list[dict] = []
    for (scenario, config), items in sorted(groups.items()):
        pooled_requests = [request for item in items for request in item["requests"]]
        duration = sum(item["observed_duration_seconds"] or 0.0 for item in items)
        summary = summarize_requests(pooled_requests, duration)
        pooled_evaluations = [
            row for item in items for row in item["evaluation_rows"]
        ]
        detection = compute_detection_metrics(pooled_evaluations)
        detection_values = [
            item["detection_latency_s"]
            for item in items
            if item["detection_latency_s"] is not None
        ]
        adaptation_values = [
            item["adaptation_latency_s"]
            for item in items
            if item["adaptation_latency_s"] is not None
        ]
        aggregates.append(
            {
                "row_type": "aggregate",
                "run_id": "",
                "scenario": scenario,
                "config": config,
                "repetition": "aggregate",
                "run_count": len(items),
                "trace_hash": ",".join(sorted({item["trace_hash"] for item in items})),
                "trace_length": items[0]["trace_length"],
                **summary,
                **detection,
                "detection_latency_s": _mean(detection_values),
                "detection_latency_samples": len(detection_values),
                "adaptation_latency_s": _mean(adaptation_values),
                "adaptation_latency_samples": len(adaptation_values),
                "limit_min": min(
                    (item["limit_min"] for item in items if item["limit_min"] is not None),
                    default=None,
                ),
                "limit_max": max(
                    (item["limit_max"] for item in items if item["limit_max"] is not None),
                    default=None,
                ),
                "limit_change_count": sum(item["limit_change_count"] for item in items),
            }
        )
    return aggregates


def _summary_row(item: dict, row_type: str) -> dict:
    return {field: item.get(field) for field in SUMMARY_FIELDS}


def _fmt(value, pattern="{:.2f}", suffix="") -> str:
    if value is None:
        return "N/A"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        if not math.isfinite(float(value)):
            return "N/A"
        return pattern.format(value) + suffix
    return str(value)


def _pct(value) -> str:
    return _fmt(value, "{:.1%}")


def _detection_rows(run_summaries: list[dict], aggregates: list[dict]) -> list[dict]:
    rows = []
    for item in run_summaries:
        if item["config"] != "adaptive":
            continue
        rows.append(
            {
                "row_type": "run",
                "scenario": item["scenario"],
                "config": item["config"],
                "repetition": item["repetition"],
                "run_count": 1,
                **{field: item.get(field) for field in DETECTION_FIELDS if field not in {"row_type", "scenario", "config", "repetition", "run_count"}},
            }
        )
    adaptive_aggregates = [item for item in aggregates if item["config"] == "adaptive"]
    for item in adaptive_aggregates:
        rows.append(
            {
                "row_type": "aggregate",
                "scenario": item["scenario"],
                "config": item["config"],
                "repetition": "aggregate",
                "run_count": item["run_count"],
                **{field: item.get(field) for field in DETECTION_FIELDS if field not in {"row_type", "scenario", "config", "repetition", "run_count"}},
            }
        )
    pooled_rows = [
        row
        for item in run_summaries
        if item["config"] == "adaptive"
        for row in item["evaluation_rows"]
    ]
    pooled = compute_detection_metrics(pooled_rows)
    detection_values = [
        item["detection_latency_s"]
        for item in run_summaries
        if item["config"] == "adaptive" and item["detection_latency_s"] is not None
    ]
    rows.append(
        {
            "row_type": "aggregate",
            "scenario": "ALL",
            "config": "adaptive",
            "repetition": "aggregate",
            "run_count": sum(
                1 for item in run_summaries if item["config"] == "adaptive"
            ),
            **pooled,
            "detection_latency_s": _mean(detection_values),
            "detection_latency_samples": len(detection_values),
        }
    )
    return rows


def _equivalence_rows(run_summaries: list[dict]) -> list[dict]:
    by_scenario_rep: dict[tuple[str, int], dict[str, dict]] = defaultdict(dict)
    for item in run_summaries:
        by_scenario_rep[(item["scenario"], item["repetition"])][item["config"]] = item
    rows = []
    for (scenario, repetition), configs in sorted(by_scenario_rep.items()):
        static = configs.get("static")
        adaptive = configs.get("adaptive")
        if static is None or adaptive is None:
            continue
        rows.append(
            {
                "scenario": scenario,
                "repetition": repetition,
                "trace_hash": static["trace_hash"],
                "static_requests": static["total_requests"],
                "adaptive_requests": adaptive["total_requests"],
                "same_trace": (
                    static["trace_hash"] == adaptive["trace_hash"]
                    and static["total_requests"] == adaptive["total_requests"]
                ),
            }
        )
    return rows


def _aggregate_lookup(aggregates: list[dict]) -> dict[tuple[str, str], dict]:
    return {
        (item["scenario"], item["config"]): item
        for item in aggregates
    }


def _repeated_lookup(repeated_stats: list[dict]) -> dict[tuple[str, str, str], dict]:
    return {
        (item["scenario"], item["config"], item["metric"]): item
        for item in repeated_stats
    }


def _ci_text(stats: dict | None, pattern: str = "{:.2f}") -> str:
    if stats is None or stats.get("mean") is None:
        return "N/A"
    mean = _fmt(stats["mean"], pattern)
    if stats.get("ci95_margin") is None:
        return f"{mean} (n={stats['n']})"
    return f"{mean} ± {_fmt(stats['ci95_margin'], pattern)} (n={stats['n']})"


def _diagnostic_summary_rows(diagnostics: dict[str, list[dict]]) -> list[dict]:
    grouped: dict[tuple[str, str], dict] = {}
    for key, field in (
        ("legitimate_rejections", "legitimate_rejections"),
        ("false_positives", "false_positives"),
        ("limit_decreases", "limit_decreases"),
    ):
        for row in diagnostics.get(key, []):
            group_key = (row["scenario"], row["config"])
            entry = grouped.setdefault(
                group_key,
                {
                    "scenario": row["scenario"],
                    "config": row["config"],
                    "legitimate_rejections": 0,
                    "false_positives": 0,
                    "limit_decreases": 0,
                },
            )
            entry[field] += 1
    return [grouped[key] for key in sorted(grouped)]


def render_markdown(
    manifest: dict,
    run_summaries: list[dict],
    aggregates: list[dict],
    detection_rows: list[dict],
    repeated_stats: list[dict],
    diagnostics: dict[str, list[dict]],
) -> str:
    lookup = _aggregate_lookup(aggregates)
    repeated_lookup = _repeated_lookup(repeated_stats)
    scenarios = [item for item in manifest.get("scenarios", [])]
    scenario_names = [item["name"] for item in scenarios]
    lines = [
        "# Phase 7 Evaluation Results",
        "",
        f"- Generated: `{manifest.get('generated_at')}`",
        f"- Seed: `{manifest.get('seed')}`",
        f"- Repetitions: `{manifest.get('repetitions')}`",
        f"- Static configurations: `{manifest.get('static_config')}`",
        f"- Adaptive configurations: `{manifest.get('adaptive_config')}`",
        f"- Model: `{json.dumps(manifest.get('model_info', {}), sort_keys=True)}`",
        "",
        "All values below were computed from the raw CSV/JSON files in this directory.",
        "These are synthetic, open-loop experiments. They are not production measurements.",
        "",
        "## Equivalent Traffic Check",
        "",
        "| Scenario | Rep | Trace SHA-256 | Static Requests | Adaptive Requests | Same Trace |",
        "|---|---:|---|---:|---:|---|",
    ]
    for row in _equivalence_rows(run_summaries):
        lines.append(
            f"| {row['scenario']} | {row['repetition']} | `{row['trace_hash'][:16]}...` | {row['static_requests']} | {row['adaptive_requests']} | {'yes' if row['same_trace'] else 'NO'} |"
        )

    lines.extend(
        [
            "",
            "## Request Outcomes",
            "",
            "| Scenario | Config | Runs | Total | Accepted | Rejected | Legit Rejected | Legit Rejection Rate |",
            "|---|---|---:|---:|---:|---:|---:|---:|",
        ]
    )
    for scenario in scenario_names:
        for config in ("static", "adaptive"):
            item = lookup.get((scenario, config))
            if item is None:
                continue
            lines.append(
                f"| {scenario} | {config} | {item['run_count']} | {item['total_requests']} | {item['accepted_requests']} | {item['rejected_requests']} | {item['legitimate_rejected']}/{item['legitimate_requests']} | {_pct(item['legitimate_rejection_rate'])} |"
            )

    lines.extend(
        [
            "",
            "## Latency and Throughput",
            "",
            "| Scenario | Config | Avg Latency (ms) | p50 (ms) | p95 (ms) | Throughput (req/s) |",
            "|---|---|---:|---:|---:|---:|",
        ]
    )
    for scenario in scenario_names:
        for config in ("static", "adaptive"):
            item = lookup.get((scenario, config))
            if item is None:
                continue
            lines.append(
                f"| {scenario} | {config} | {_fmt(item['latency_avg_ms'])} | {_fmt(item['latency_p50_ms'])} | {_fmt(item['latency_p95_ms'])} | {_fmt(item['throughput_rps'])} |"
            )

    lines.extend(
        [
            "",
            "## Detection Quality and Adaptation Delay",
            "",
            "Detection metrics are reported for the adaptive configuration only. The static configuration does not call the ML detector.",
            "",
            "| Scenario | Config | Runs | Evaluations | TP | FP | FN | TN | Precision | Recall | F1 | Detection Delay (s) | Adaptation Delay (s) |",
            "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
        ]
    )
    for row in detection_rows:
        if row["scenario"] == "ALL" or row["row_type"] != "aggregate":
            continue
        lines.append(
            f"| {row['scenario']} | {row['config']} | {row['run_count']} | {row['detection_evaluations']} | {row['true_positives']} | {row['false_positives']} | {row['false_negatives']} | {row['true_negatives']} | {_fmt(row['precision'])} | {_fmt(row['recall'])} | {_fmt(row['f1'])} | {_fmt(row['detection_latency_s'])} | {_fmt(lookup.get((row['scenario'], row['config']), {}).get('adaptation_latency_s'))} |"
        )
    all_row = next((row for row in detection_rows if row["scenario"] == "ALL"), None)
    if all_row is not None:
        lines.append(
            f"| ALL | adaptive | {all_row['run_count']} | {all_row['detection_evaluations']} | {all_row['true_positives']} | {all_row['false_positives']} | {all_row['false_negatives']} | {all_row['true_negatives']} | {_fmt(all_row['precision'])} | {_fmt(all_row['recall'])} | {_fmt(all_row['f1'])} | {_fmt(all_row['detection_latency_s'])} | N/A |"
        )

    repeated_metric_order = [
        ("legitimate_rejection_rate", "Legitimate rejection rate"),
        ("rejected_requests", "Rejected requests"),
        ("latency_p95_ms", "p95 latency (ms)"),
        ("throughput_rps", "Throughput (req/s)"),
        ("anomaly_precision", "Detection precision"),
        ("anomaly_recall", "Detection recall"),
        ("anomaly_f1", "Detection F1"),
        ("detection_delay_s", "Detection delay (s)"),
        ("adaptation_delay_s", "Adaptation delay (s)"),
        ("limit_min", "Minimum limit"),
        ("limit_max", "Maximum limit"),
    ]
    has_repeats = any(item.get("run_count", 1) > 1 for item in repeated_stats)
    if has_repeats:
        lines.extend(
            [
                "",
                "## Repeated-Run Reliability (95% CI)",
                "",
                "Each repetition uses an independent deterministic trace (`seed = base seed + repetition - 1`).",
                "The unit of analysis is one run. Intervals are two-sided 95% Student's t intervals for the mean across repetitions.",
                "",
            ]
        )
        for scenario in scenario_names:
            rows = []
            for metric, label in repeated_metric_order:
                static_stats = repeated_lookup.get((scenario, "static", metric))
                adaptive_stats = repeated_lookup.get((scenario, "adaptive", metric))
                if static_stats is None and adaptive_stats is None:
                    continue
                rows.append((metric, label, static_stats, adaptive_stats))
            if not rows:
                continue
            lines.extend(
                [
                    f"### {scenario}",
                    "",
                    "| Metric | Static mean ± 95% CI | Adaptive mean ± 95% CI |",
                    "|---|---:|---:|",
                ]
            )
            percentage_metrics = {
                "legitimate_rejection_rate",
                "anomaly_precision",
                "anomaly_recall",
                "anomaly_f1",
            }
            for metric, label, static_stats, adaptive_stats in rows:
                pattern = "{:.1%}" if metric in percentage_metrics else "{:.2f}"
                lines.append(
                    f"| {label} | {_ci_text(static_stats, pattern)} | {_ci_text(adaptive_stats, pattern)} |"
                )
            lines.append("")

    diagnostic_summary = _diagnostic_summary_rows(diagnostics)
    lines.extend(
        [
            "",
            "## Failure Diagnostics",
            "",
            "Counts below are per scenario and configuration. Per-event context is available in the diagnostics CSV files.",
            "Status-derived limit and decision fields are the nearest poll snapshot, not proof of the decision that caused an event.",
            "",
            "| Scenario | Config | Legitimate Rejections | False Positives | Limit Decreases |",
            "|---|---|---:|---:|---:|",
        ]
    )
    for row in diagnostic_summary:
        lines.append(
            f"| {row['scenario']} | {row['config']} | {row['legitimate_rejections']} | {row['false_positives']} | {row['limit_decreases']} |"
        )

    lines.extend(
        [
            "",
            "## Interpretation Guardrails",
            "",
            "- These experiments use synthetic labelled traffic; they are not a real-world production evaluation.",
            "- Detection labels are assigned from the evaluation timestamp. A window that straddles a phase boundary can therefore be labelled differently from its exact contents.",
            "- The open-loop trace is replayed at fixed offsets; throughput reflects this scripted offered load, not maximum server capacity.",
            "- Static and adaptive runs execute sequentially on the same machine, so small timing and host-load differences remain.",
            "- Status polling adds a small read-only request load to both configurations.",
            "- A measured difference in this report is descriptive only. No claim that the adaptive limiter is universally better is made unless the measurements support it.",
            "",
            "## Raw Artifacts",
            "",
            "- `summary_runs.csv`: one row per run.",
            "- `summary_aggregate.csv`: pooled values per scenario and configuration.",
            "- `summary_repeated.csv`: mean, sample standard deviation, and 95% CI across repetitions.",
            "- `detection_metrics.csv`: adaptive detection metrics per run and aggregate.",
            "- `diagnostics_legitimate_rejections.csv`: legitimate requests rejected with nearest-poll context.",
            "- `diagnostics_false_positives.csv`: adaptive anomaly predictions where the label was normal.",
            "- `diagnostics_limit_decreases.csv`: observed limit decreases and their nearest-poll context.",
            "- `diagnostics_summary.csv`: diagnostic event counts by scenario and configuration.",
            "- `results.json`: structured manifest, per-run summaries, aggregates, repeated statistics, diagnostics, and detection metrics.",
            "- `raw/requests_*.csv`: per-request outcomes, latency, labels, and scheduled offsets.",
            "- `raw/status_*.csv`: adaptive status snapshots used for limit and detection timing.",
            "- `raw/run_*.json`: run metadata, configuration, final status, and trace hash.",
            "- `charts/*.svg`: dependency-free charts generated from the same measurements.",
            "",
        ]
    )
    return "\n".join(lines)


def generate_reports(
    output_dir: Path,
    manifest: dict,
    run_records: list[dict],
    scenarios: dict[str, Scenario],
) -> dict:
    output_dir.mkdir(parents=True, exist_ok=True)
    charts_dir = output_dir / "charts"
    charts_dir.mkdir(parents=True, exist_ok=True)

    run_summaries = []
    for run in run_records:
        summary = build_run_summary(run, scenarios[run["scenario"]])
        summary.update(
            {
                "row_type": "run",
                "run_id": run["run_id"],
                "scenario": run["scenario"],
                "config": run["config"],
                "repetition": run["repetition"],
                "run_count": 1,
                "trace_hash": run["trace_hash"],
                "trace_length": run["trace_length"],
                "requests": run["requests"],
                "status_rows": run["status_rows"],
            }
        )
        run_summaries.append(summary)
    aggregates = build_aggregates(run_summaries)
    detection_rows = _detection_rows(run_summaries, aggregates)
    repeated_stats = build_repeated_stats(run_summaries)
    diagnostics = build_diagnostic_tables(run_summaries, scenarios)
    diagnostic_summary = _diagnostic_summary_rows(diagnostics)

    write_csv(
        output_dir / "summary_runs.csv",
        [_summary_row(item, "run") for item in run_summaries],
        SUMMARY_FIELDS,
    )
    write_csv(
        output_dir / "summary_aggregate.csv",
        [_summary_row(item, "aggregate") for item in aggregates],
        SUMMARY_FIELDS,
    )
    write_csv(output_dir / "summary_repeated.csv", repeated_stats, REPEATED_FIELDS)
    write_csv(output_dir / "detection_metrics.csv", detection_rows, DETECTION_FIELDS)
    write_csv(
        output_dir / "diagnostics_legitimate_rejections.csv",
        diagnostics["legitimate_rejections"],
        REJECTION_FIELDS,
    )
    write_csv(
        output_dir / "diagnostics_false_positives.csv",
        diagnostics["false_positives"],
        FALSE_POSITIVE_FIELDS,
    )
    write_csv(
        output_dir / "diagnostics_limit_decreases.csv",
        diagnostics["limit_decreases"],
        DECREASE_FIELDS,
    )
    write_csv(
        output_dir / "diagnostics_summary.csv",
        diagnostic_summary,
        DIAGNOSTIC_SUMMARY_FIELDS,
    )

    lookup = _aggregate_lookup(aggregates)
    scenario_names = [scenario.name for scenario in scenarios.values()]
    bar_chart_svg(
        charts_dir / "legitimate_rejection_rate.svg",
        "Legitimate-Request Rejection Rate",
        scenario_names,
        {
            config: [
                lookup.get((scenario, config), {}).get("legitimate_rejection_rate")
                for scenario in scenario_names
            ]
            for config in ("static", "adaptive")
        },
        "Rejection rate",
        "{:.1%}",
    )
    bar_chart_svg(
        charts_dir / "latency_p95.svg",
        "p95 Request Latency",
        scenario_names,
        {
            config: [
                lookup.get((scenario, config), {}).get("latency_p95_ms")
                for scenario in scenario_names
            ]
            for config in ("static", "adaptive")
        },
        "Milliseconds",
        "{:.1f}",
    )
    bar_chart_svg(
        charts_dir / "throughput.svg",
        "Observed Throughput Under the Scripted Trace",
        scenario_names,
        {
            config: [
                lookup.get((scenario, config), {}).get("throughput_rps")
                for scenario in scenario_names
            ]
            for config in ("static", "adaptive")
        },
        "Requests / second",
        "{:.2f}",
    )
    detection_scenarios = [
        scenario
        for scenario in scenario_names
        if lookup.get((scenario, "adaptive"), {}).get("detection_latency_samples", 0) > 0
    ]
    bar_chart_svg(
        charts_dir / "detection_latency.svg",
        "Time to First True Anomaly Detection (Adaptive)",
        detection_scenarios,
        {
            "adaptive": [
                lookup[(scenario, "adaptive")]["detection_latency_s"]
                for scenario in detection_scenarios
            ]
        },
        "Seconds from anomaly-phase start",
        "{:.2f}",
    )
    adaptation_scenarios = [
        scenario
        for scenario in scenario_names
        if lookup.get((scenario, "adaptive"), {}).get("adaptation_latency_samples", 0) > 0
    ]
    bar_chart_svg(
        charts_dir / "adaptation_latency.svg",
        "Time to First Limit Adaptation (Adaptive)",
        adaptation_scenarios,
        {
            "adaptive": [
                lookup[(scenario, "adaptive")]["adaptation_latency_s"]
                for scenario in adaptation_scenarios
            ]
        },
        "Seconds from trigger-phase start",
        "{:.2f}",
    )

    repeated_lookup = _repeated_lookup(repeated_stats)
    if any(item.get("run_count", 1) > 1 for item in repeated_stats):
        ci_specs = [
            (
                "legitimate_rejection_rate_ci.svg",
                "Legitimate-Request Rejection Rate (mean ± 95% CI)",
                "legitimate_rejection_rate",
                "Rejection rate",
                "{:.1%}",
            ),
            (
                "latency_p95_ci.svg",
                "p95 Request Latency (mean ± 95% CI)",
                "latency_p95_ms",
                "Milliseconds",
                "{:.1f}",
            ),
            (
                "throughput_ci.svg",
                "Observed Throughput (mean ± 95% CI)",
                "throughput_rps",
                "Requests / second",
                "{:.2f}",
            ),
        ]
        for filename, title, metric, y_label, value_format in ci_specs:
            series = {}
            errors = {}
            for config in ("static", "adaptive"):
                series[config] = []
                errors[config] = []
                for scenario in scenario_names:
                    stats = repeated_lookup.get((scenario, config, metric))
                    series[config].append(stats["mean"] if stats else None)
                    errors[config].append(stats["ci95_margin"] if stats else None)
            bar_chart_svg(
                charts_dir / filename,
                title,
                scenario_names,
                series,
                y_label,
                value_format,
                errors=errors,
            )
        for filename, title, metric, y_label in (
            (
                "detection_latency_ci.svg",
                "Time to First True Anomaly Detection (mean ± 95% CI)",
                "detection_delay_s",
                "Seconds from anomaly-phase start",
            ),
            (
                "adaptation_latency_ci.svg",
                "Time to First Limit Adaptation (mean ± 95% CI)",
                "adaptation_delay_s",
                "Seconds from trigger-phase start",
            ),
        ):
            series = {"adaptive": []}
            errors = {"adaptive": []}
            for scenario in scenario_names:
                stats = repeated_lookup.get((scenario, "adaptive", metric))
                series["adaptive"].append(stats["mean"] if stats else None)
                errors["adaptive"].append(stats["ci95_margin"] if stats else None)
            bar_chart_svg(
                charts_dir / filename,
                title,
                scenario_names,
                series,
                y_label,
                "{:.2f}",
                errors=errors,
            )

    for scenario in scenario_names:
        series: dict[str, list[tuple[float, float]]] = {}
        for config in ("static", "adaptive"):
            run = next(
                (
                    item
                    for item in run_records
                    if item["scenario"] == scenario and item["config"] == config
                ),
                None,
            )
            if run is None:
                continue
            series[config] = [
                (row["sampled_elapsed_seconds"], row["current_limit"])
                for row in sorted(
                    run["status_rows"], key=lambda row: row["sampled_elapsed_seconds"]
                )
                if row.get("current_limit") is not None
            ]
        line_chart_svg(
            charts_dir / f"limit_timeline_{scenario}.svg",
            f"Rate-Limit Timeline: {scenario}",
            series,
            "Elapsed seconds",
            "Current limit",
            "{:.0f}",
        )

    comparison = render_markdown(
        manifest,
        run_summaries,
        aggregates,
        detection_rows,
        repeated_stats,
        diagnostics,
    )
    (output_dir / "comparison.md").write_text(comparison, encoding="utf-8")

    results_payload = {
        "manifest": manifest,
        "run_summaries": [
            {key: value for key, value in item.items() if key not in {"requests", "status_rows", "evaluation_rows"}}
            for item in run_summaries
        ],
        "aggregates": aggregates,
        "repeated_stats": repeated_stats,
        "detection_metrics": detection_rows,
        "diagnostics_summary": diagnostic_summary,
        "diagnostics": diagnostics,
        "equivalence": _equivalence_rows(run_summaries),
    }
    write_json(output_dir / "results.json", results_payload)

    generated_files = sorted(
        str(path.relative_to(output_dir)).replace("\\", "/")
        for path in output_dir.rglob("*")
        if path.is_file()
    )
    manifest["generated_files"] = generated_files
    manifest["generated_at"] = manifest.get("generated_at") or datetime.now(
        timezone.utc
    ).isoformat()
    write_json(output_dir / "manifest.json", manifest)
    return {
        "run_summaries": run_summaries,
        "aggregates": aggregates,
        "detection_rows": detection_rows,
        "comparison_markdown": comparison,
    }
