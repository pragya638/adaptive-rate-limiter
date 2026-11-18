from __future__ import annotations

import json
from pathlib import Path

import pytest

from evaluation.runner import (
    ADAPTIVE_PROPERTIES,
    BASE_PROPERTIES,
    build_backend_args,
    load_run_records,
    parse_instant,
    seed_for_repetition,
    validate_run_seeds,
)


def test_parse_instant_accepts_java_iso_strings() -> None:
    assert parse_instant("2026-01-01T00:00:00Z") == 1767225600.0
    assert parse_instant(None) is None


def test_static_backend_args_use_static_mode() -> None:
    args = build_backend_args(Path("app.jar"), 18081, "static", "http://127.0.0.1:18000")
    assert "java" == args[0]
    assert "--server.port=18081" in args
    assert "--adaptive.enabled=false" in args
    assert "--rate-limit.max-requests=5" in args
    assert "--rate-limit.window=2s" in args
    assert "--monitoring.observation-interval=2s" in args
    assert all("adaptive-rate-limiter.initial-limit" not in item for item in args)


def test_adaptive_backend_args_use_live_ml_and_engine_bounds() -> None:
    args = build_backend_args(Path("app.jar"), 18081, "adaptive", "http://127.0.0.1:18000")
    assert "--adaptive.enabled=true" in args
    assert "--ml-service.base-url=http://127.0.0.1:18000" in args
    assert "--adaptive-rate-limiter.initial-limit=5" in args
    assert "--adaptive-rate-limiter.min-limit=1" in args
    assert "--adaptive-rate-limiter.max-limit=50" in args
    assert "--adaptive-rate-limiter.anomaly-confirmation-windows=1" in args
    assert "--adaptive.cooldown-seconds=0" in args


def test_both_configurations_share_base_rate_limit_values() -> None:
    static = build_backend_args(Path("app.jar"), 18081, "static", "http://x")
    adaptive = build_backend_args(Path("app.jar"), 18081, "adaptive", "http://x")
    for key, value in BASE_PROPERTIES.items():
        flag = f"--{key}={value}"
        assert flag in static
        assert flag in adaptive
    assert ADAPTIVE_PROPERTIES["adaptive-rate-limiter.initial-limit"] == "5"


def test_seed_for_repetition_is_deterministic_and_distinct() -> None:
    assert seed_for_repetition(2026, 1) == 2026
    assert seed_for_repetition(2026, 2) == 2027
    assert seed_for_repetition(2026, 2) == seed_for_repetition(2026, 2)
    with pytest.raises(ValueError):
        seed_for_repetition(2026, 0)


def test_validate_run_seeds_derives_base_seed_and_rejects_mismatch() -> None:
    runs = [
        {"run_id": "a", "seed": 2026, "repetition": 1},
        {"run_id": "b", "seed": 2027, "repetition": 2},
    ]
    assert validate_run_seeds(runs) == 2026
    runs.append({"run_id": "c", "seed": 2030, "repetition": 3})
    with pytest.raises(RuntimeError):
        validate_run_seeds(runs)


def test_load_run_records_reads_sorted_raw_json(tmp_path) -> None:
    raw = tmp_path / "raw"
    raw.mkdir()
    payloads = [
        {"run_id": "r2", "scenario": "normal", "config": "static", "repetition": 2},
        {"run_id": "r1", "scenario": "normal", "config": "adaptive", "repetition": 1},
    ]
    for payload in payloads:
        (raw / f"run_{payload['run_id']}.json").write_text(
            json.dumps(payload), encoding="utf-8"
        )
    records = load_run_records(tmp_path)
    assert [record["run_id"] for record in records] == ["r1", "r2"]
