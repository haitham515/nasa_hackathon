"""SIMULATED physiological signals for one astronaut. NOT REAL MEASUREMENTS.

Each metric = personal baseline + daily rhythm + slow random walk (mean-reverting)
+ the offset of any active anomaly episode. Heart rate is sampled every second;
the biomarkers every 5 seconds, standing in for a hypothetical in-situ biosensor
(real assays take hours; the cadence is accelerated so the demo shows change).
"""
from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class MetricModel:
    baseline: float
    noise: float          # std of the random-walk step
    reversion: float      # pull back toward baseline per step (0..1)
    circadian_amp: float  # amplitude of the 24 h rhythm
    floor: float
    ceiling: float
    decimals: int
    cadence_s: int        # seconds between samples


METRICS: dict[str, MetricModel] = {
    "heart_rate": MetricModel(baseline=68, noise=1.1, reversion=0.08, circadian_amp=5, floor=30, ceiling=200,
                              decimals=0, cadence_s=1),
    "crp": MetricModel(baseline=1.2, noise=0.04, reversion=0.06, circadian_amp=0.1, floor=0.05, ceiling=40,
                       decimals=2, cadence_s=5),
    "fibrinogen": MetricModel(baseline=3.0, noise=0.03, reversion=0.06, circadian_amp=0.08, floor=0.5, ceiling=9,
                              decimals=2, cadence_s=5),
    "troponin_i": MetricModel(baseline=6.0, noise=0.35, reversion=0.06, circadian_amp=0.4, floor=0.5, ceiling=500,
                              decimals=1, cadence_s=5),
}

DAY_S = 86_400


class SignalGenerator:
    """Stateful generator. `offsets` lets the scenario engine push a metric away from baseline."""

    def __init__(self, seed: int | None = None):
        self.rng = np.random.default_rng(seed)
        self.state = {k: 0.0 for k in METRICS}

    def sample(self, metric: str, t_epoch: float, offset: float = 0.0) -> float:
        m = METRICS[metric]
        self.state[metric] = self.state[metric] * (1 - m.reversion) + self.rng.normal(0, m.noise)
        circadian = m.circadian_amp * math.sin(2 * math.pi * (t_epoch % DAY_S) / DAY_S)
        value = m.baseline + circadian + self.state[metric] + offset
        return round(float(min(m.ceiling, max(m.floor, value))), m.decimals)

    @staticmethod
    def due(metric: str, tick: int) -> bool:
        return tick % METRICS[metric].cadence_s == 0
