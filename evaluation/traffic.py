"""Deterministic traffic scenarios and trace generation for Phase 7."""

from __future__ import annotations

import hashlib
import json
import random
from dataclasses import dataclass
from typing import Iterable

NORMAL_PATH = "/api/demo"
ERROR_PATH = "/api/missing"
CLIENT_ID = "phase7-evaluation-client"

OBSERVATION_INTERVAL_SECONDS = 2.0
STATIC_LIMIT = 5
ADAPTIVE_INITIAL_LIMIT = 5
TRACE_SEED = 2026


@dataclass(frozen=True)
class Phase:
    name: str
    start_seconds: float
    end_seconds: float
    requests_per_second: float
    path: str
    legitimate: bool
    ground_truth: str

    @property
    def duration_seconds(self) -> float:
        return self.end_seconds - self.start_seconds


@dataclass(frozen=True)
class Scenario:
    name: str
    description: str
    phases: tuple[Phase, ...]
    adaptation_phase: str | None
    adaptation_direction: str | None
    detection_phase: str | None

    @property
    def total_duration_seconds(self) -> float:
        return self.phases[-1].end_seconds

    def phase(self, name: str) -> Phase | None:
        for phase in self.phases:
            if phase.name == name:
                return phase
        return None

    def phase_at(self, elapsed_seconds: float) -> Phase | None:
        if elapsed_seconds < 0.0:
            return None
        for phase in self.phases:
            if phase.start_seconds <= elapsed_seconds < phase.end_seconds:
                return phase
        return None

    def ground_truth_at(self, elapsed_seconds: float) -> str:
        phase = self.phase_at(elapsed_seconds)
        return "normal" if phase is None else phase.ground_truth


def _build_scenario(
    name: str,
    description: str,
    phase_specs: Iterable[tuple[str, float, float, str, bool, str]],
    adaptation_phase: str | None,
    adaptation_direction: str | None,
    detection_phase: str | None,
) -> Scenario:
    phases: list[Phase] = []
    elapsed = 0.0
    for phase_name, duration, rps, path, legitimate, ground_truth in phase_specs:
        end = elapsed + duration
        phases.append(
            Phase(
                name=phase_name,
                start_seconds=elapsed,
                end_seconds=end,
                requests_per_second=rps,
                path=path,
                legitimate=legitimate,
                ground_truth=ground_truth,
            )
        )
        elapsed = end
    return Scenario(
        name=name,
        description=description,
        phases=tuple(phases),
        adaptation_phase=adaptation_phase,
        adaptation_direction=adaptation_direction,
        detection_phase=detection_phase,
    )


def build_scenarios() -> dict[str, Scenario]:
    scenarios = [
        _build_scenario(
            name="normal",
            description="Sustained low-rate legitimate traffic below the configured limit.",
            phase_specs=[
                ("normal", 10.0, 2.0, NORMAL_PATH, True, "normal"),
            ],
            adaptation_phase=None,
            adaptation_direction=None,
            detection_phase=None,
        ),
        _build_scenario(
            name="legitimate_burst",
            description="Low-rate baseline, a high-rate legitimate burst, and a normal tail.",
            phase_specs=[
                ("baseline", 4.0, 2.0, NORMAL_PATH, True, "normal"),
                ("burst", 4.0, 8.0, NORMAL_PATH, True, "normal"),
                ("tail", 4.0, 2.0, NORMAL_PATH, True, "normal"),
            ],
            adaptation_phase="burst",
            adaptation_direction="any",
            detection_phase=None,
        ),
        _build_scenario(
            name="error_heavy",
            description="Normal baseline, error-heavy traffic, then normal recovery traffic.",
            phase_specs=[
                ("baseline", 4.0, 2.0, NORMAL_PATH, True, "normal"),
                ("abnormal", 6.0, 3.0, ERROR_PATH, False, "anomaly"),
                ("recovery", 4.0, 2.0, NORMAL_PATH, True, "normal"),
            ],
            adaptation_phase="abnormal",
            adaptation_direction="decrease",
            detection_phase="abnormal",
        ),
        _build_scenario(
            name="recovery",
            description="Error-heavy traffic followed by a long normal recovery period.",
            phase_specs=[
                ("abnormal", 6.0, 3.0, ERROR_PATH, False, "anomaly"),
                ("recovery", 12.0, 2.0, NORMAL_PATH, True, "normal"),
            ],
            adaptation_phase="recovery",
            adaptation_direction="increase",
            detection_phase="abnormal",
        ),
    ]
    return {scenario.name: scenario for scenario in scenarios}


def generate_trace(scenario: Scenario, seed: int = TRACE_SEED) -> list[dict]:
    rng = random.Random(f"{seed}:{scenario.name}")
    trace: list[dict] = []
    for phase in scenario.phases:
        request_count = int(round(phase.requests_per_second * phase.duration_seconds))
        if request_count <= 0:
            continue
        spacing = phase.duration_seconds / request_count
        for index in range(request_count):
            jitter = rng.uniform(-0.15, 0.15) * spacing
            offset = phase.start_seconds + (index + 0.5) * spacing + jitter
            offset = max(phase.start_seconds + 0.001, min(phase.end_seconds - 0.001, offset))
            trace.append(
                {
                    "offset_seconds": round(offset, 6),
                    "path": phase.path,
                    "legitimate": phase.legitimate,
                    "ground_truth": phase.ground_truth,
                    "phase": phase.name,
                    "client_id": CLIENT_ID,
                }
            )
    trace.sort(key=lambda item: (item["offset_seconds"], item["path"], item["phase"]))
    for index, item in enumerate(trace):
        item["index"] = index
    return trace


def trace_hash(trace: list[dict]) -> str:
    canonical = [
        {
            "offset_seconds": item["offset_seconds"],
            "path": item["path"],
            "legitimate": item["legitimate"],
            "ground_truth": item["ground_truth"],
            "phase": item["phase"],
        }
        for item in trace
    ]
    payload = json.dumps(canonical, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def phase_summary(scenario: Scenario) -> list[dict]:
    return [
        {
            "name": phase.name,
            "start_seconds": phase.start_seconds,
            "end_seconds": phase.end_seconds,
            "requests_per_second": phase.requests_per_second,
            "path": phase.path,
            "legitimate": phase.legitimate,
            "ground_truth": phase.ground_truth,
        }
        for phase in scenario.phases
    ]


def assert_equivalent_traces(left: list[dict], right: list[dict]) -> None:
    if len(left) != len(right):
        raise AssertionError(f"trace lengths differ: {len(left)} != {len(right)}")
    if trace_hash(left) != trace_hash(right):
        raise AssertionError("trace hashes differ")
    for index, (left_item, right_item) in enumerate(zip(left, right)):
        for key in ("offset_seconds", "path", "legitimate", "ground_truth", "phase"):
            if left_item[key] != right_item[key]:
                raise AssertionError(f"trace item {index} differs on {key}")
