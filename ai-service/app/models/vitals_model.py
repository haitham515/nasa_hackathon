"""Isolation Forest on SIMULATED heart rate / HRV. Trained on simulated pre-flight
readings; features are deviations from each astronaut's simulated resting values."""
from __future__ import annotations

import joblib
import numpy as np
from sklearn.ensemble import IsolationForest

from app import config
from app.simulator import vitals as sim

CONTAMINATION = 0.02


def _features(participant_id: str, hr: float, hrv: float, baselines: dict) -> np.ndarray:
    b = baselines[participant_id]
    return np.array([hr - b["hr"], np.log(hrv / b["hrv"])])


def train() -> dict:
    data = sim.baseline_set()
    baselines = {
        pid: {
            "hr": float(np.mean([d["hr_bpm"] for d in data if d["participant_id"] == pid])),
            "hrv": float(np.mean([d["hrv_rmssd_ms"] for d in data if d["participant_id"] == pid])),
        }
        for pid in sim.PROFILES
    }
    X = np.array([_features(d["participant_id"], d["hr_bpm"], d["hrv_rmssd_ms"], baselines) for d in data])
    model = IsolationForest(n_estimators=300, contamination=CONTAMINATION, random_state=config.RANDOM_STATE).fit(X)
    artifact = {"model": model, "baselines": baselines, "source": sim.SOURCE}
    config.ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump(artifact, config.VITALS_MODEL_FILE)
    return artifact


_cache: dict | None = None


def load() -> dict:
    global _cache
    if _cache is None:
        _cache = joblib.load(config.VITALS_MODEL_FILE) if config.VITALS_MODEL_FILE.exists() else train()
    return _cache


def reset_cache() -> None:
    global _cache
    _cache = None


def score(participant_id: str, hr_bpm: float, hrv_rmssd_ms: float) -> dict:
    art = load()
    if participant_id not in art["baselines"]:
        raise KeyError(f"No simulated baseline for {participant_id}")
    x = _features(participant_id, hr_bpm, hrv_rmssd_ms, art["baselines"]).reshape(1, -1)
    b = art["baselines"][participant_id]
    return {
        "model": "isolation_forest_vitals",
        "source": sim.SOURCE,
        "anomaly_score": round(float(-art["model"].decision_function(x)[0]), 4),
        "threshold": 0.0,
        "is_atypical": bool(art["model"].predict(x)[0] == -1),
        "hr_delta_bpm": round(hr_bpm - b["hr"], 1),
        "hrv_delta_pct": round((hrv_rmssd_ms / b["hrv"] - 1) * 100, 1),
        "baseline_hr_bpm": round(b["hr"], 1),
        "baseline_hrv_ms": round(b["hrv"], 1),
    }
