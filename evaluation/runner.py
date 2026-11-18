"""Live experiment runner for static and adaptive rate-limiter configurations."""

from __future__ import annotations

import argparse
import http.client
import json
import os
import platform
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

try:
    from .report import (
        REQUEST_FIELDS,
        STATUS_FIELDS,
        generate_reports,
        write_csv,
        write_json,
    )
    from .traffic import (
        ADAPTIVE_INITIAL_LIMIT,
        CLIENT_ID,
        NORMAL_PATH,
        OBSERVATION_INTERVAL_SECONDS,
        STATIC_LIMIT,
        TRACE_SEED,
        assert_equivalent_traces,
        build_scenarios,
        generate_trace,
        phase_summary,
        trace_hash,
    )
except ImportError:
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from evaluation.report import (
        REQUEST_FIELDS,
        STATUS_FIELDS,
        generate_reports,
        write_csv,
        write_json,
    )
    from evaluation.traffic import (
        ADAPTIVE_INITIAL_LIMIT,
        CLIENT_ID,
        NORMAL_PATH,
        OBSERVATION_INTERVAL_SECONDS,
        STATIC_LIMIT,
        TRACE_SEED,
        assert_equivalent_traces,
        build_scenarios,
        generate_trace,
        phase_summary,
        trace_hash,
    )

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_JAR = REPO_ROOT / "target" / "adaptive-rate-limiter-0.1.0.jar"
ML_SERVICE_DIR = REPO_ROOT / "ml-service"
WINDOW_SECONDS = 2.0
STATUS_POLL_INTERVAL_SECONDS = 0.1
QUIET_WARMUP_WINDOWS = 1
SETTLE_SECONDS = 3.0

BASE_PROPERTIES = {
    "rate-limit.enabled": "true",
    "rate-limit.max-requests": str(STATIC_LIMIT),
    "rate-limit.window": f"{WINDOW_SECONDS:g}s",
    "monitoring.observation-interval": f"{OBSERVATION_INTERVAL_SECONDS:g}s",
}

ADAPTIVE_PROPERTIES = {
    "adaptive.enabled": "true",
    "adaptive.cooldown-seconds": "0",
    "adaptive-rate-limiter.min-limit": "1",
    "adaptive-rate-limiter.max-limit": "50",
    "adaptive-rate-limiter.initial-limit": str(ADAPTIVE_INITIAL_LIMIT),
    "adaptive-rate-limiter.increase-step": "5",
    "adaptive-rate-limiter.decrease-step": "10",
    "adaptive-rate-limiter.decrease-fraction": "0.2",
    "adaptive-rate-limiter.recovery-step": "1",
    "adaptive-rate-limiter.recovery-windows": "2",
    "adaptive-rate-limiter.high-error-rate-threshold": "0.1",
    "adaptive-rate-limiter.spike-ratio-threshold": "1.5",
    "adaptive-rate-limiter.growth-ratio-threshold": "1.2",
    "adaptive-rate-limiter.anomaly-confirmation-windows": "1",
    "adaptive-rate-limiter.growth-confirmation-windows": "1",
}

STATIC_CONFIG = {
    **BASE_PROPERTIES,
    "adaptive.enabled": "false",
    "ml-service.base-url": "http://127.0.0.1:18000",
}

ADAPTIVE_CONFIG = {
    **BASE_PROPERTIES,
    **ADAPTIVE_PROPERTIES,
    "ml-service.base-url": "http://127.0.0.1:18000",
    "ml-service.connect-timeout-ms": "500",
    "ml-service.read-timeout-ms": "500",
}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def seed_for_repetition(base_seed: int, repetition: int) -> int:
    """Deterministic per-repetition seed shared by static and adaptive runs.

    Repetition 1 reuses ``base_seed`` so existing single-run artifacts remain
    comparable. Each later repetition shifts the seed by one, producing an
    independent trace while remaining reproducible.
    """
    if repetition < 1:
        raise ValueError("repetition must be >= 1")
    return base_seed + repetition - 1


def load_run_records(output_dir: Path) -> list[dict]:
    """Load previously written raw run JSON files for report-only aggregation."""
    raw_dir = output_dir / "raw"
    records: list[dict] = []
    for path in sorted(raw_dir.glob("run_*.json")):
        records.append(json.loads(path.read_text(encoding="utf-8")))
    records.sort(
        key=lambda run: (
            int(run.get("repetition") or 0),
            run.get("scenario") or "",
            run.get("config") or "",
        )
    )
    return records


def validate_run_seeds(run_records: list[dict]) -> int | None:
    """Return the base seed, rejecting raw runs produced with another seed."""
    base_seed: int | None = None
    for run in run_records:
        seed = run.get("seed")
        repetition = run.get("repetition")
        if seed is None or not repetition:
            continue
        derived = int(seed) - int(repetition) + 1
        if base_seed is None:
            base_seed = derived
        elif derived != base_seed:
            raise RuntimeError(
                "raw runs use inconsistent base seeds: "
                f"{base_seed} and {derived} ({run.get('run_id')})"
            )
    return base_seed


def verify_trace_equivalence(run_records: list[dict]) -> None:
    grouped: dict[tuple[str, int], dict[str, dict]] = {}
    for run in run_records:
        key = (run["scenario"], int(run["repetition"]))
        grouped.setdefault(key, {})[run["config"]] = run
    for static_run, adaptive_run in (
        (configs.get("static"), configs.get("adaptive"))
        for configs in grouped.values()
    ):
        if static_run is None or adaptive_run is None:
            continue
        assert_equivalent_traces(
            [
                {
                    "offset_seconds": item["scheduled_offset_seconds"],
                    "path": item["path"],
                    "legitimate": item["legitimate"],
                    "ground_truth": item["ground_truth"],
                    "phase": item["phase"],
                }
                for item in static_run["requests"]
            ],
            [
                {
                    "offset_seconds": item["scheduled_offset_seconds"],
                    "path": item["path"],
                    "legitimate": item["legitimate"],
                    "ground_truth": item["ground_truth"],
                    "phase": item["phase"],
                }
                for item in adaptive_run["requests"]
            ],
        )


def parse_instant(value: str | None) -> float | None:
    if not value:
        return None
    text = value.replace("Z", "+00:00")
    return datetime.fromisoformat(text).timestamp()


def read_json(url: str, timeout: float = 2.0) -> dict:
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(url, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def wait_for_json(url: str, predicate, timeout: float = 60.0, interval: float = 0.2):
    deadline = time.monotonic() + timeout
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            payload = read_json(url)
            if predicate(payload):
                return payload
        except Exception as exc:
            last_error = exc
        time.sleep(interval)
    raise RuntimeError(f"timed out waiting for {url}: {last_error}")


def check_port_free(port: int) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        if sock.connect_ex(("127.0.0.1", port)) == 0:
            raise RuntimeError(f"port {port} is already in use")


def build_backend_args(
    jar: Path,
    port: int,
    config: str,
    ml_url: str,
) -> list[str]:
    properties = dict(BASE_PROPERTIES)
    if config == "adaptive":
        properties.update(ADAPTIVE_PROPERTIES)
        properties.update(
            {
                "adaptive.enabled": "true",
                "ml-service.base-url": ml_url,
                "ml-service.connect-timeout-ms": "500",
                "ml-service.read-timeout-ms": "500",
            }
        )
    else:
        properties["adaptive.enabled"] = "false"
        properties["ml-service.base-url"] = ml_url
    args = [
        "java",
        "-jar",
        str(jar),
        f"--server.port={port}",
        "--server.address=127.0.0.1",
        "--spring.main.banner-mode=off",
    ]
    args.extend(f"--{key}={value}" for key, value in properties.items())
    return args


def start_process(
    command: list[str],
    cwd: Path,
    log_path: Path,
    environment: dict | None = None,
):
    log_path.parent.mkdir(parents=True, exist_ok=True)
    handle = log_path.open("w", encoding="utf-8")
    creationflags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    process = subprocess.Popen(
        command,
        cwd=str(cwd),
        env=environment,
        stdout=handle,
        stderr=subprocess.STDOUT,
        creationflags=creationflags,
    )
    return process, handle


def stop_process(process, handle) -> None:
    if process is not None and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=8)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
    if handle is not None:
        handle.close()


def start_ml_service(
    port: int,
    output_dir: Path,
    observation_interval: float,
    python_executable: str,
):
    check_port_free(port)
    environment = os.environ.copy()
    environment["ML_OBSERVATION_INTERVAL_SECONDS"] = str(observation_interval)
    environment["ML_LOG_LEVEL"] = "warning"
    command = [
        python_executable,
        "-m",
        "uvicorn",
        "app.main:app",
        "--host",
        "127.0.0.1",
        "--port",
        str(port),
    ]
    process, handle = start_process(
        command,
        ML_SERVICE_DIR,
        output_dir / "logs" / "ml-service.log",
        environment,
    )
    try:
        wait_for_json(
            f"http://127.0.0.1:{port}/health",
            lambda payload: payload.get("model_ready") is True,
            timeout=90,
        )
        model_info = read_json(f"http://127.0.0.1:{port}/model/info")
        actual_interval = float(model_info["observation_interval_seconds"])
        if abs(actual_interval - observation_interval) > 1e-9:
            raise RuntimeError(
                "ML observation interval does not match the Java monitoring interval: "
                f"{actual_interval} != {observation_interval}"
            )
        return process, handle, model_info
    except Exception:
        stop_process(process, handle)
        raise


def wait_for_backend(base_url: str, adaptive: bool, timeout: float = 90.0) -> None:
    wait_for_json(
        f"{base_url}/api/health",
        lambda payload: payload.get("status") == "UP",
        timeout=timeout,
    )
    wait_for_json(
        f"{base_url}/api/adaptive/status",
        lambda payload: bool(payload.get("enabled")) is adaptive,
        timeout=30,
    )


def wait_for_monitoring_sequence(base_url: str, minimum: int, timeout: float = 20.0) -> int:
    payload = wait_for_json(
        f"{base_url}/api/adaptive/status",
        lambda item: item.get("trafficWindow", {}).get("sequence", 0) >= minimum,
        timeout=timeout,
        interval=0.1,
    )
    return int(payload["trafficWindow"]["sequence"])


def wait_for_rate_limit_boundary(window_seconds: float, offset_seconds: float = 0.025) -> None:
    window_ms = int(window_seconds * 1000)
    now_ms = int(time.time() * 1000)
    target_ms = ((now_ms // window_ms) + 1) * window_ms + int(offset_seconds * 1000)
    delay = (target_ms - now_ms) / 1000.0
    if delay > 0:
        time.sleep(delay)


def send_request(base_url: str, path: str, timeout: float = 5.0):
    start = time.perf_counter()
    parsed = urllib.parse.urlparse(base_url)
    connection = http.client.HTTPConnection(parsed.hostname, parsed.port, timeout=timeout)
    try:
        connection.request(
            "GET",
            path,
            headers={"X-Client-Id": CLIENT_ID, "Connection": "close"},
        )
        response = connection.getresponse()
        response.read()
        latency_ms = (time.perf_counter() - start) * 1000.0
        return response.status, latency_ms, ""
    except Exception as exc:
        latency_ms = (time.perf_counter() - start) * 1000.0
        return 0, latency_ms, str(exc)
    finally:
        connection.close()


def _execute_request(request: dict, base_url: str, start_monotonic: float) -> dict:
    sent_offset = time.perf_counter() - start_monotonic
    status, latency_ms, error = send_request(base_url, request["path"])
    completed_offset = time.perf_counter() - start_monotonic
    return {
        "request_index": request["index"],
        "scheduled_offset_seconds": request["offset_seconds"],
        "sent_offset_seconds": sent_offset,
        "completed_offset_seconds": completed_offset,
        "latency_ms": latency_ms,
        "status": status,
        "error": error,
        "allowed": status > 0 and status != 429,
        "rejected": status == 429,
        "legitimate": request["legitimate"],
        "phase": request["phase"],
        "ground_truth": request["ground_truth"],
        "path": request["path"],
    }


def replay_trace(
    base_url: str,
    trace: list[dict],
    max_workers: int = 64,
    start_epoch: float | None = None,
    start_monotonic: float | None = None,
):
    start_epoch = time.time() if start_epoch is None else start_epoch
    start_monotonic = (
        time.perf_counter() if start_monotonic is None else start_monotonic
    )
    results: list[dict] = []
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = []
        for request in trace:
            target = start_monotonic + request["offset_seconds"]
            delay = target - time.perf_counter()
            if delay > 0:
                time.sleep(delay)
            futures.append(executor.submit(_execute_request, request, base_url, start_monotonic))
        for future in futures:
            results.append(future.result())
    results.sort(key=lambda item: item["request_index"])
    observed_duration = max(
        (item["completed_offset_seconds"] for item in results), default=0.0
    )
    return results, start_epoch, start_monotonic, observed_duration


class StatusPoller:
    def __init__(
        self,
        base_url: str,
        start_epoch: float,
        start_monotonic: float,
        interval_seconds: float = STATUS_POLL_INTERVAL_SECONDS,
    ) -> None:
        self.base_url = base_url
        self.start_epoch = start_epoch
        self.start_monotonic = start_monotonic
        self.interval_seconds = interval_seconds
        self.rows: list[dict] = []
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, name="status-poller", daemon=True)

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        self._thread.join(timeout=5)

    def sample(self) -> dict:
        status = read_json(f"{self.base_url}/api/adaptive/status")
        sampled_epoch = time.time()
        sampled_elapsed = time.perf_counter() - self.start_monotonic
        window = status.get("trafficWindow") or {}
        anomaly = status.get("latestAnomaly") or {}
        decision = status.get("lastDecision") or {}
        last_evaluation_epoch = parse_instant(status.get("lastEvaluationAt"))
        last_adaptation_epoch = parse_instant(status.get("lastAdaptationAt"))
        return {
            "sampled_at": datetime.fromtimestamp(
                sampled_epoch, tz=timezone.utc
            ).isoformat(),
            "sampled_elapsed_seconds": sampled_elapsed,
            "enabled": status.get("enabled"),
            "mode": status.get("mode"),
            "current_limit": status.get("currentLimit"),
            "window_sequence": window.get("sequence"),
            "window_start": window.get("start"),
            "window_end": window.get("end"),
            "window_duration_seconds": window.get("durationSeconds"),
            "window_request_count": window.get("requestCount"),
            "window_allowed_requests": window.get("allowedRequests"),
            "window_rejected_requests": window.get("rejectedRequests"),
            "window_client_error_rate": window.get("clientErrorRate"),
            "window_request_rate": window.get("requestRate"),
            "window_ewma_request_rate": window.get("ewmaRequestRate"),
            "last_evaluation_at": status.get("lastEvaluationAt"),
            "last_evaluation_elapsed_seconds": (
                last_evaluation_epoch - self.start_epoch
                if last_evaluation_epoch is not None
                else None
            ),
            "last_adaptation_at": status.get("lastAdaptationAt"),
            "last_adaptation_elapsed_seconds": (
                last_adaptation_epoch - self.start_epoch
                if last_adaptation_epoch is not None
                else None
            ),
            "anomaly_available": anomaly.get("available") if anomaly else None,
            "anomaly_is_anomaly": anomaly.get("isAnomaly") if anomaly else None,
            "anomaly_score": anomaly.get("anomalyScore") if anomaly else None,
            "anomaly_prediction": anomaly.get("prediction") if anomaly else None,
            "anomaly_model": anomaly.get("model") if anomaly else None,
            "decision_action": decision.get("action") if decision else None,
            "decision_previous_limit": decision.get("previousLimit") if decision else None,
            "decision_applied_limit": decision.get("appliedLimit") if decision else None,
            "decision_reason": decision.get("reasonCode") if decision else None,
            "decision_evaluated_at": decision.get("evaluatedAt") if decision else None,
        }

    def _run(self) -> None:
        while not self._stop.is_set():
            try:
                self.rows.append(self.sample())
            except Exception:
                pass
            self._stop.wait(self.interval_seconds)


def run_single(
    jar: Path,
    scenario,
    config: str,
    repetition: int,
    port: int,
    ml_url: str,
    model_info: dict,
    output_dir: Path,
    seed: int,
) -> dict:
    trace = generate_trace(scenario, seed=seed)
    run_id = f"{scenario.name}_{config}_r{repetition}"
    base_url = f"http://127.0.0.1:{port}"
    print(f"[run] {run_id} requests={len(trace)} trace={trace_hash(trace)[:12]}")
    check_port_free(port)
    command = build_backend_args(jar, port, config, ml_url)
    process, handle = start_process(
        command,
        REPO_ROOT,
        output_dir / "logs" / f"{run_id}.log",
        os.environ.copy(),
    )
    try:
        wait_for_backend(base_url, adaptive=(config == "adaptive"))
        initial_sequence = wait_for_monitoring_sequence(
            base_url, minimum=QUIET_WARMUP_WINDOWS
        )
        wait_for_rate_limit_boundary(WINDOW_SECONDS)
        start_epoch = time.time()
        start_monotonic = time.perf_counter()
        start_iso = datetime.fromtimestamp(start_epoch, tz=timezone.utc).isoformat()
        poller = StatusPoller(base_url, start_epoch, start_monotonic)
        poller.start()
        requests, _, _, observed_duration = replay_trace(
            base_url,
            trace,
            start_epoch=start_epoch,
            start_monotonic=start_monotonic,
        )
        time.sleep(max(SETTLE_SECONDS, 2 * OBSERVATION_INTERVAL_SECONDS))
        poller.stop()
        final_status = read_json(f"{base_url}/api/adaptive/status")
        status_rows = poller.rows
    finally:
        stop_process(process, handle)

    backend_args = build_backend_args(jar, port, config, ml_url)
    backend_properties = {}
    for argument in backend_args:
        if argument.startswith("--") and "=" in argument:
            key, value = argument[2:].split("=", 1)
            backend_properties[key] = value
    run = {
        "run_id": run_id,
        "scenario": scenario.name,
        "config": config,
        "repetition": repetition,
        "seed": seed,
        "trace_hash": trace_hash(trace),
        "trace_length": len(trace),
        "phases": phase_summary(scenario),
        "experiment_start_epoch": start_epoch,
        "experiment_started_at": start_iso,
        "observed_duration_seconds": observed_duration,
        "quiet_warmup_windows": QUIET_WARMUP_WINDOWS,
        "start_sequence": initial_sequence,
        "backend_properties": backend_properties,
        "model_info": model_info if config == "adaptive" else None,
        "base_url": base_url,
        "final_status": final_status,
        "requests": requests,
        "status_rows": status_rows,
    }
    write_json(output_dir / "raw" / f"run_{run_id}.json", run)
    write_csv(
        output_dir / "raw" / f"requests_{run_id}.csv",
        [{**request, "run_id": run_id, "scenario": scenario.name, "config": config, "repetition": repetition} for request in requests],
        REQUEST_FIELDS,
    )
    write_csv(
        output_dir / "raw" / f"status_{run_id}.csv",
        [{**row, "run_id": run_id, "scenario": scenario.name, "config": config, "repetition": repetition} for row in status_rows],
        STATUS_FIELDS,
    )
    return run


def git_value(args: list[str]) -> str:
    try:
        return subprocess.check_output(
            ["git", *args], cwd=REPO_ROOT, text=True, stderr=subprocess.DEVNULL
        ).strip()
    except Exception:
        return ""


def java_version() -> str:
    try:
        return subprocess.check_output(
            ["java", "-version"], cwd=REPO_ROOT, text=True, stderr=subprocess.STDOUT
        ).splitlines()[0]
    except Exception:
        return ""


def resolve_python(python_executable: str | None) -> str:
    if python_executable:
        return python_executable
    venv_python = ML_SERVICE_DIR / ".venv" / "Scripts" / "python.exe"
    if venv_python.exists():
        return str(venv_python)
    return sys.executable


def build_manifest(
    args,
    scenarios: dict,
    model_info: dict,
    ml_url: str,
    run_records: list[dict],
    report_only: bool,
    previous: dict | None = None,
) -> dict:
    previous = previous or {}
    base_seed = validate_run_seeds(run_records)
    if base_seed is None:
        base_seed = args.seed
    inferred_repetitions = max(
        (int(run.get("repetition") or 0) for run in run_records), default=0
    )
    manifest = dict(previous) if report_only else {}
    first_generated_at = (
        manifest.get("generated_at") if report_only else None
    ) or utc_now()
    manifest.update(
        {
            "phase": "Phase 7",
            "generated_at": first_generated_at,
            "report_generated_at": utc_now(),
            "command": " ".join([sys.executable, *sys.argv]),
            "cwd": str(REPO_ROOT),
            "platform": platform.platform(),
            "python_version": platform.python_version(),
            "java_version": java_version(),
            "git_branch": git_value(["branch", "--show-current"]),
            "git_commit": git_value(["rev-parse", "HEAD"]),
            "seed": base_seed,
            "repetitions": max(args.repetitions, inferred_repetitions),
            "start_repetition": args.start_repetition,
            "report_only": report_only,
            "scenarios": [
                {
                    "name": scenario.name,
                    "description": scenario.description,
                    "total_duration_seconds": scenario.total_duration_seconds,
                    "phases": phase_summary(scenario),
                }
                for scenario in scenarios.values()
            ],
            "static_config": {**STATIC_CONFIG, "ml-service.base-url": ml_url},
            "adaptive_config": {**ADAPTIVE_CONFIG, "ml-service.base-url": ml_url},
            "observation_interval_seconds": args.observation_interval,
            "status_poll_interval_seconds": STATUS_POLL_INTERVAL_SECONDS,
            "quiet_warmup_windows": QUIET_WARMUP_WINDOWS,
            "settle_seconds": SETTLE_SECONDS,
            "model_info": model_info or manifest.get("model_info", {}),
            "ml_url": ml_url,
            "run_count": len(run_records),
            "raw_directory": str(Path(args.output_dir) / "raw"),
            "notes": [
                "Synthetic labelled traffic, not production traffic.",
                "Each repetition uses seed = base seed + repetition - 1; static and adaptive share the same seed.",
                "Static and adaptive configurations replay the same deterministic trace within a repetition.",
                "Detection metrics are reported only for the adaptive configuration.",
            ],
        }
    )
    return manifest


def main(argv: list[str] | None = None) -> int:
    global SETTLE_SECONDS
    parser = argparse.ArgumentParser(description="Run the Phase 7 evaluation experiment.")
    parser.add_argument("--config", choices=["all", "static", "adaptive"], default="all")
    parser.add_argument(
        "--scenario",
        choices=["all", "normal", "legitimate_burst", "error_heavy", "recovery"],
        default="all",
    )
    parser.add_argument("--repetitions", type=int, default=1)
    parser.add_argument(
        "--start-repetition",
        type=int,
        default=1,
        help="First repetition to execute (for resumable chunked runs).",
    )
    parser.add_argument(
        "--report-only",
        action="store_true",
        help="Skip running experiments and regenerate reports from raw/run_*.json.",
    )
    parser.add_argument("--seed", type=int, default=TRACE_SEED)
    parser.add_argument("--output-dir", default=str(REPO_ROOT / "evaluation" / "results"))
    parser.add_argument("--jar", default=str(DEFAULT_JAR))
    parser.add_argument("--port", type=int, default=18081)
    parser.add_argument("--ml-port", type=int, default=18000)
    parser.add_argument("--ml-python", default=None)
    parser.add_argument("--observation-interval", type=float, default=OBSERVATION_INTERVAL_SECONDS)
    parser.add_argument("--settle-seconds", type=float, default=SETTLE_SECONDS)
    args = parser.parse_args(argv)

    SETTLE_SECONDS = args.settle_seconds
    if args.repetitions < 1:
        parser.error("--repetitions must be >= 1")
    if args.start_repetition < 1:
        parser.error("--start-repetition must be >= 1")
    if args.start_repetition > args.repetitions:
        parser.error("--start-repetition must be <= --repetitions")
    if abs(args.observation_interval - OBSERVATION_INTERVAL_SECONDS) > 1e-9:
        parser.error(
            "this framework currently requires "
            f"--observation-interval={OBSERVATION_INTERVAL_SECONDS}"
        )
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "raw").mkdir(parents=True, exist_ok=True)
    (output_dir / "logs").mkdir(parents=True, exist_ok=True)

    ml_url = f"http://127.0.0.1:{args.ml_port}"
    previous_manifest: dict | None = None
    manifest_path = output_dir / "manifest.json"
    if args.report_only and manifest_path.exists():
        try:
            previous_manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            previous_manifest = None

    if args.report_only:
        run_records = load_run_records(output_dir)
        if not run_records:
            parser.error(f"no raw run files found in {output_dir / 'raw'}")
        validate_run_seeds(run_records)
        all_scenarios = build_scenarios()
        present = sorted({run["scenario"] for run in run_records})
        if args.scenario != "all":
            present = [name for name in present if name == args.scenario]
            run_records = [
                run for run in run_records if run["scenario"] == args.scenario
            ]
            if not run_records:
                parser.error(f"no raw runs for scenario {args.scenario}")
        scenarios = {name: all_scenarios[name] for name in present}
        if previous_manifest and previous_manifest.get("ml_url"):
            ml_url = previous_manifest["ml_url"]
        model_info = (previous_manifest or {}).get("model_info", {})
        verify_trace_equivalence(run_records)
        print(f"[report-only] loaded {len(run_records)} raw runs from {output_dir}")
    else:
        jar = Path(args.jar)
        if not jar.exists():
            parser.error(
                f"jar not found: {jar}. Build it with '.\\mvnw.cmd -DskipTests package'."
            )
        scenarios = build_scenarios()
        if args.scenario != "all":
            scenarios = {args.scenario: scenarios[args.scenario]}
        configs = (
            ["static", "adaptive"] if args.config == "all" else [args.config]
        )
        python_executable = resolve_python(args.ml_python)
        ml_process = ml_handle = None
        model_info: dict = {}
        run_records: list[dict] = []
        try:
            if "adaptive" in configs:
                ml_process, ml_handle, model_info = start_ml_service(
                    args.ml_port,
                    output_dir,
                    args.observation_interval,
                    python_executable,
                )
            for repetition in range(args.start_repetition, args.repetitions + 1):
                seed = seed_for_repetition(args.seed, repetition)
                for scenario in scenarios.values():
                    order = list(configs)
                    if len(order) == 2 and repetition % 2 == 0:
                        order.reverse()
                    for config in order:
                        run_records.append(
                            run_single(
                                jar=jar,
                                scenario=scenario,
                                config=config,
                                repetition=repetition,
                                port=args.port,
                                ml_url=ml_url,
                                model_info=model_info,
                                output_dir=output_dir,
                                seed=seed,
                            )
                        )
            verify_trace_equivalence(run_records)
        finally:
            stop_process(ml_process, ml_handle)

    manifest = build_manifest(
        args,
        scenarios,
        model_info,
        ml_url,
        run_records,
        args.report_only,
        previous_manifest,
    )
    generate_reports(output_dir, manifest, run_records, scenarios)
    print(f"[done] {len(run_records)} runs in reports at {output_dir}")
    print(f"[done] comparison: {output_dir / 'comparison.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
