"""SIMULATED heart rate / HRV generator. NOT NASA DATA.

Purpose: demonstrate the real-time pipeline (ingest -> score -> alert) with a
continuous signal, because no downloadable in-flight wearable data for this crew
was found. Every reading produced here carries source="SIMULATED".

Only the DIRECTION of the effects is anchored to a publication; magnitudes are
invented, plausible values:
  Overbey/Mason et al., Nature 2024, "Molecular and physiological changes in the
  SpaceX Inspiration4 civilian crew" (Apple Watch Series 6 data):
    - only C004 showed a decreased heart rate and increased HRV in flight;
    - C001 and C002 showed increased heart rate post-flight vs pre-flight.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np

SOURCE = "SIMULATED"
PAPER = "https://www.nature.com/articles/s41586-024-07648-x"


@dataclass(frozen=True)
class Profile:
    hr_rest: float      # resting heart rate, bpm (invented)
    hrv_rest: float     # RMSSD, ms (invented)
    inflight_hr: float  # bpm shift in flight
    inflight_hrv: float # fractional HRV shift in flight
    r1_hr: float        # bpm shift at R+1
    r1_hrv: float       # fractional HRV shift at R+1


PROFILES: dict[str, Profile] = {
    "C001": Profile(62, 48, +6, -0.15, +14, -0.30),
    "C002": Profile(68, 42, +7, -0.15, +12, -0.25),
    "C003": Profile(71, 38, +5, -0.10, +2, -0.05),
    "C004": Profile(58, 55, -5, +0.20, +1, 0.00),
}

HR_NOISE = 2.5
HRV_NOISE = 0.08  # fractional


def expected(participant_id: str, segment: str) -> tuple[float, float]:
    """Expected (hr, hrv) for a mission segment: PRE_FLIGHT, IN_FLIGHT, R+1, or later recovery."""
    p = PROFILES[participant_id]
    if segment == "IN_FLIGHT":
        return p.hr_rest + p.inflight_hr, p.hrv_rest * (1 + p.inflight_hrv)
    if segment == "R+1":
        return p.hr_rest + p.r1_hr, p.hrv_rest * (1 + p.r1_hrv)
    return p.hr_rest, p.hrv_rest  # pre-flight baseline and recovered post-flight


def sample(participant_id: str, segment: str, rng: np.random.Generator) -> dict:
    hr, hrv = expected(participant_id, segment)
    return {
        "participant_id": participant_id,
        "hr_bpm": round(float(hr + rng.normal(0, HR_NOISE)), 1),
        "hrv_rmssd_ms": round(float(max(5.0, hrv * (1 + rng.normal(0, HRV_NOISE)))), 1),
        "source": SOURCE,
    }


def baseline_set(n_per_participant: int = 300, seed: int = 7) -> list[dict]:
    """Simulated pre-flight readings used to train the vitals model."""
    rng = np.random.default_rng(seed)
    return [sample(pid, "PRE_FLIGHT", rng) for pid in PROFILES for _ in range(n_per_participant)]
