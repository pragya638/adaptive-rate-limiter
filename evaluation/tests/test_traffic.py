from __future__ import annotations

import pytest

from evaluation.traffic import (
    ERROR_PATH,
    NORMAL_PATH,
    assert_equivalent_traces,
    build_scenarios,
    generate_trace,
    trace_hash,
)


def test_scenario_names_and_durations() -> None:
    scenarios = build_scenarios()
    assert list(scenarios) == [
        "normal",
        "legitimate_burst",
        "error_heavy",
        "recovery",
    ]
    assert scenarios["normal"].total_duration_seconds == 10.0
    assert scenarios["legitimate_burst"].total_duration_seconds == 12.0
    assert scenarios["error_heavy"].total_duration_seconds == 14.0
    assert scenarios["recovery"].total_duration_seconds == 18.0


def test_trace_generation_is_deterministic() -> None:
    scenario = build_scenarios()["error_heavy"]
    first = generate_trace(scenario, seed=2026)
    second = generate_trace(scenario, seed=2026)
    assert trace_hash(first) == trace_hash(second)
    assert first == second


def test_different_seeds_produce_different_offsets() -> None:
    scenario = build_scenarios()["normal"]
    first = generate_trace(scenario, seed=1)
    second = generate_trace(scenario, seed=2)
    assert trace_hash(first) != trace_hash(second)


def test_trace_counts_and_labels() -> None:
    scenarios = build_scenarios()
    expected = {
        "normal": 20,
        "legitimate_burst": 48,
        "error_heavy": 34,
        "recovery": 42,
    }
    for name, scenario in scenarios.items():
        trace = generate_trace(scenario)
        assert len(trace) == expected[name]
        assert all(item["offset_seconds"] >= 0 for item in trace)
        offsets = [item["offset_seconds"] for item in trace]
        assert offsets == sorted(offsets)
        assert [item["index"] for item in trace] == list(range(len(trace)))


def test_abnormal_requests_are_labelled_and_use_error_path() -> None:
    scenario = build_scenarios()["error_heavy"]
    trace = generate_trace(scenario)
    abnormal = [item for item in trace if item["phase"] == "abnormal"]
    assert abnormal
    assert all(item["path"] == ERROR_PATH for item in abnormal)
    assert all(item["ground_truth"] == "anomaly" for item in abnormal)
    assert all(item["legitimate"] is False for item in abnormal)
    normal = [item for item in trace if item["phase"] != "abnormal"]
    assert all(item["path"] == NORMAL_PATH for item in normal)
    assert all(item["legitimate"] is True for item in normal)


def test_burst_is_labelled_as_legitimate_normal_traffic() -> None:
    scenario = build_scenarios()["legitimate_burst"]
    burst = [item for item in generate_trace(scenario) if item["phase"] == "burst"]
    assert burst
    assert all(item["legitimate"] is True for item in burst)
    assert all(item["ground_truth"] == "normal" for item in burst)


def test_ground_truth_lookup() -> None:
    scenario = build_scenarios()["recovery"]
    assert scenario.ground_truth_at(0.0) == "anomaly"
    assert scenario.ground_truth_at(5.99) == "anomaly"
    assert scenario.ground_truth_at(6.0) == "normal"
    assert scenario.ground_truth_at(100.0) == "normal"


def test_equivalent_traces_accept_equal_payloads() -> None:
    scenario = build_scenarios()["normal"]
    assert_equivalent_traces(generate_trace(scenario), generate_trace(scenario))


def test_equivalent_traces_reject_modified_payloads() -> None:
    scenario = build_scenarios()["normal"]
    left = generate_trace(scenario)
    right = generate_trace(scenario)
    right[0]["path"] = "/changed"
    with pytest.raises(AssertionError):
        assert_equivalent_traces(left, right)
