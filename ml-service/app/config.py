"""Runtime configuration for the anomaly detection service.

All settings have safe local-development defaults and can be overridden with
environment variables prefixed with ``ML_``. No secrets, credentials, or
production URLs are stored here.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

# Environment variable names (documented in the README).
ENV_HOST = "ML_HOST"
ENV_PORT = "ML_PORT"
ENV_LOG_LEVEL = "ML_LOG_LEVEL"
ENV_CONTAMINATION = "ML_CONTAMINATION"
ENV_N_ESTIMATORS = "ML_N_ESTIMATORS"
ENV_RANDOM_STATE = "ML_RANDOM_STATE"
ENV_TRAINING_SAMPLES = "ML_TRAINING_SAMPLES"

# Isolation Forest expects contamination in the open interval (0, 0.5].
MIN_CONTAMINATION = 0.001
MAX_CONTAMINATION = 0.5


def _get_str(name: str, default: str) -> str:
    value = os.getenv(name)
    return default if value is None or value.strip() == "" else value.strip()


def _get_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        return int(raw)
    except ValueError as exc:  # pragma: no cover - defensive, exercised via config tests
        raise ValueError(f"{name} must be an integer, got {raw!r}") from exc


def _get_float(name: str, default: float) -> float:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        return float(raw)
    except ValueError as exc:  # pragma: no cover - defensive, exercised via config tests
        raise ValueError(f"{name} must be a number, got {raw!r}") from exc


@dataclass(frozen=True)
class Settings:
    """Immutable service settings."""

    host: str = "127.0.0.1"
    port: int = 8000
    log_level: str = "info"

    # Model hyper-parameters.
    contamination: float = 0.05
    n_estimators: int = 200
    random_state: int = 42
    training_samples: int = 2000

    def __post_init__(self) -> None:
        if not self.host:
            raise ValueError("host must not be empty")
        if not 1 <= self.port <= 65535:
            raise ValueError(f"port must be between 1 and 65535, got {self.port}")
        if not MIN_CONTAMINATION <= self.contamination <= MAX_CONTAMINATION:
            raise ValueError(
                "contamination must be between "
                f"{MIN_CONTAMINATION} and {MAX_CONTAMINATION}, got {self.contamination}"
            )
        if self.n_estimators < 1:
            raise ValueError("n_estimators must be >= 1")
        if self.training_samples < 10:
            raise ValueError("training_samples must be >= 10")

    @classmethod
    def from_env(cls) -> "Settings":
        """Build settings from environment variables, falling back to defaults."""
        return cls(
            host=_get_str(ENV_HOST, "127.0.0.1"),
            port=_get_int(ENV_PORT, 8000),
            log_level=_get_str(ENV_LOG_LEVEL, "info").lower(),
            contamination=_get_float(ENV_CONTAMINATION, 0.05),
            n_estimators=_get_int(ENV_N_ESTIMATORS, 200),
            random_state=_get_int(ENV_RANDOM_STATE, 42),
            training_samples=_get_int(ENV_TRAINING_SAMPLES, 2000),
        )
