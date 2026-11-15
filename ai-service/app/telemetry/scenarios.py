"""Anomaly episodes injected into the SIMULATED stream (random, or on demand for testing).

An episode ramps a metric away from baseline (25 % of its duration), holds (50 %),
then recovers (25 %). Peaks are chosen so some episodes stay at ATTENTION and
others cross the REVIEW REQUIRED limits defined in the backend.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class Scenario:
    key: str
    metric: str
    label: str
    offset_range: tuple[float, float]    # added to the signal at the plateau
    duration_range: tuple[float, float]  # seconds
    weight: float                        # relative frequency for random episodes


SCENARIOS: dict[str, Scenario] = {
    "tachycardia_mild": Scenario("tachycardia_mild", "heart_rate", "Mild tachycardia", (36, 46), (25, 45), 3),
    "tachycardia": Scenario("tachycardia", "heart_rate", "Tachycardia", (64, 78), (40, 70), 2),
    "bradycardia": Scenario("bradycardia", "heart_rate", "Bradycardia", (-34, -28), (30, 50), 1),
    "crp_rise": Scenario("crp_rise", "crp", "Inflammatory CRP rise", (4, 11), (120, 200), 1.5),
    "fibrinogen_rise": Scenario("fibrinogen_rise", "fibrinogen", "Fibrinogen rise", (1.6, 2.6), (120, 180), 1),
    "troponin_bump": Scenario("troponin_bump", "troponin_i", "Troponin elevation", (25, 55), (120, 180), 0.7),
}

# What the "inject test anomaly" buttons trigger: strong enough to reach REVIEW REQUIRED.
INJECT_MAP: dict[str, tuple[str, float]] = {
    "heart_rate": ("tachycardia", 1.0),
    "heart_rate_low": ("bradycardia", 1.0),
    "crp": ("crp_rise", 1.0),
    "fibrinogen": ("fibrinogen_rise", 1.0),
    "troponin_i": ("troponin_bump", 1.0),
}


@dataclass
class Episode:
    scenario: Scenario
    start: float
    duration: float
    peak_offset: float

    def offset(self, t: float) -> float:
        x = (t - self.start) / self.duration
        if x < 0 or x > 1:
            return 0.0
        if x < 0.25:
            return self.peak_offset * (x / 0.25)
        if x < 0.75:
            return self.peak_offset
        return self.peak_offset * ((1 - x) / 0.25)

    def finished(self, t: float) -> bool:
        return t > self.start + self.duration

    def public(self, t: float) -> dict:
        return {"scenario": self.scenario.key, "label": self.scenario.label, "metric": self.scenario.metric,
                "remaining_s": round(max(0.0, self.start + self.duration - t), 1)}


class ScenarioEngine:
    """Schedules random episodes (Poisson process) and accepts manual injections."""

    def __init__(self, rng: np.random.Generator, mean_interval_s: float):
        self.rng = rng
        self.mean_interval_s = mean_interval_s
        self.active: list[Episode] = []
        self.next_at: float | None = None

    def _schedule_next(self, t: float) -> None:
        self.next_at = t + float(self.rng.exponential(self.mean_interval_s)) if self.mean_interval_s > 0 else None

    def start(self, key: str, t: float, intensity: float | None = None) -> Episode:
        sc = SCENARIOS[key]
        lo, hi = sc.offset_range
        level = intensity if intensity is not None else float(self.rng.uniform(0, 1))
        ep = Episode(sc, t, float(self.rng.uniform(*sc.duration_range)), lo + (hi - lo) * level)
        # One episode per metric at a time.
        self.active = [e for e in self.active if e.scenario.metric != sc.metric]
        self.active.append(ep)
        return ep

    def inject(self, metric_or_key: str, t: float) -> Episode:
        key, intensity = INJECT_MAP.get(metric_or_key, (metric_or_key, 1.0))
        if key not in SCENARIOS:
            raise KeyError(f"Unknown scenario or metric: {metric_or_key}")
        return self.start(key, t, intensity)

    def offsets(self, t: float) -> dict[str, float]:
        if self.next_at is None and self.mean_interval_s > 0:
            self._schedule_next(t)
        if self.next_at is not None and t >= self.next_at:
            keys = list(SCENARIOS)
            weights = np.array([SCENARIOS[k].weight for k in keys])
            self.start(str(self.rng.choice(keys, p=weights / weights.sum())), t)
            self._schedule_next(t)
        self.active = [e for e in self.active if not e.finished(t)]
        out: dict[str, float] = {}
        for e in self.active:
            out[e.scenario.metric] = out.get(e.scenario.metric, 0.0) + e.offset(t)
        return out
