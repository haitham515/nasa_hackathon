"""Replay engine: feeds the backend as if devices were reporting in real time.

  * NASA biomarker panels (REAL, OSD-575) are replayed at their sampling timepoints.
  * Heart rate / HRV readings (SIMULATED) are emitted between panels, including
    the 3-day in-flight segment, so the live pipeline can be demonstrated.

Mission time is compressed: the whole L-92 -> R+194 timeline plays in ~25 s at
speed 1. Every reading is POSTed to the backend's ingestion endpoint, exactly
like a real device integration would be.
"""
from __future__ import annotations

import logging
import threading
import time
from dataclasses import dataclass, field

import httpx
import numpy as np
import pandas as pd

from app import config
from app.simulator import vitals as sim

log = logging.getLogger("astra.replay")

VITALS_TICKS_PER_SEGMENT = 5
BASE_TICK_SECONDS = 0.5
SIM_SEED = 2026

# (segment label, mission phase, NASA timepoint or None, first day, last day)
SEGMENTS: list[tuple[str, str, str | None, int, int]] = [
    ("L-92", "PRE_FLIGHT", "L-92", -96, -92),
    ("L-44", "PRE_FLIGHT", "L-44", -48, -44),
    ("L-3", "PRE_FLIGHT", "L-3", -7, -3),
    ("IN_FLIGHT", "IN_FLIGHT", None, 0, 3),
    ("R+1", "POST_FLIGHT", "R+1", 3, 4),
    ("R+45", "POST_FLIGHT", "R+45", 44, 48),
    ("R+82", "POST_FLIGHT", "R+82", 81, 85),
    ("R+194", "POST_FLIGHT", "R+194", 193, 197),
]


def _vitals_segment(segment: str) -> str:
    if segment == "IN_FLIGHT":
        return "IN_FLIGHT"
    if segment == "R+1":
        return "R+1"
    return "PRE_FLIGHT"  # pre-flight and recovered post-flight share the resting profile


def build_events(simulate: bool = True) -> list[list[dict]]:
    """Return ticks; each tick is a list of readings sent together."""
    long_df = pd.read_csv(config.PROCESSED_LONG_FILE)
    rng = np.random.default_rng(SIM_SEED)
    participants = sorted(long_df["participant_id"].unique())
    ticks: list[list[dict]] = []
    for label, phase, timepoint, d0, d1 in SEGMENTS:
        if simulate:
            for day in np.linspace(d0, d1, VITALS_TICKS_PER_SEGMENT):
                tick = []
                for pid in participants:
                    v = sim.sample(pid, _vitals_segment(label), rng)
                    tick.append({
                        "source": sim.SOURCE, "kind": "VITALS", "participantId": pid,
                        "timepoint": label, "missionPhase": phase, "dayOffset": round(float(day), 2),
                        "hrBpm": v["hr_bpm"], "hrvRmssdMs": v["hrv_rmssd_ms"],
                    })
                ticks.append(tick)
        if timepoint:
            tick = []
            for pid in participants:
                rows = long_df[(long_df["participant_id"] == pid) & (long_df["timepoint"] == timepoint)]
                if rows.empty:
                    continue
                tick.append({
                    "source": "NASA", "kind": "BIOMARKER_PANEL", "participantId": pid,
                    "timepoint": timepoint, "missionPhase": phase,
                    "dayOffset": float(config.TIMEPOINTS[timepoint]),
                    "sampleName": rows["sample_name"].iloc[0], "study": config.STUDY_ID,
                    "measurements": [
                        {
                            "analyteKey": r.analyte_key, "nasaLabel": r.analyte_nasa_label,
                            "concentration": float(r.concentration), "unit": r.unit,
                            "percent": float(r.percent_of_crew_preflight_mean),
                            "atAssayUpperLimit": bool(r.at_assay_upper_limit),
                        }
                        for r in rows.itertuples()
                    ],
                })
            ticks.append(tick)
    return ticks


@dataclass
class ReplayState:
    running: bool = False
    run_id: int | None = None
    speed: float = 1.0
    simulate: bool = True
    tick: int = 0
    total_ticks: int = 0
    sent: int = 0
    errors: int = 0
    last_error: str | None = None
    segment: str | None = None
    day_offset: float | None = None
    _stop: threading.Event = field(default_factory=threading.Event, repr=False)
    _thread: threading.Thread | None = field(default=None, repr=False)

    def public(self) -> dict:
        return {k: v for k, v in self.__dict__.items() if not k.startswith("_")}


class ReplayEngine:
    def __init__(self, backend_url: str = config.BACKEND_URL):
        self.backend_url = backend_url.rstrip("/")
        self.state = ReplayState()
        self._lock = threading.Lock()

    def start(self, run_id: int | None, speed: float = 1.0, simulate: bool = True) -> dict:
        with self._lock:
            self.stop()
            ticks = build_events(simulate)
            self.state = ReplayState(running=True, run_id=run_id, speed=max(0.1, speed), simulate=simulate,
                                     total_ticks=len(ticks))
            t = threading.Thread(target=self._run, args=(ticks, self.state), daemon=True)
            self.state._thread = t
            t.start()
            return self.state.public()

    def stop(self) -> None:
        st = self.state
        if st._thread and st._thread.is_alive():
            st._stop.set()
            st._thread.join(timeout=5)
        st.running = False

    def _run(self, ticks: list[list[dict]], st: ReplayState) -> None:
        interval = BASE_TICK_SECONDS / st.speed
        with httpx.Client(timeout=10) as client:
            for i, tick in enumerate(ticks):
                if st._stop.is_set():
                    break
                for reading in tick:
                    reading["runId"] = st.run_id
                    try:
                        client.post(f"{self.backend_url}/api/readings", json=reading).raise_for_status()
                        st.sent += 1
                    except httpx.HTTPError as exc:
                        st.errors += 1
                        st.last_error = str(exc)
                        log.warning("ingest failed: %s", exc)
                st.tick = i + 1
                st.segment = tick[0]["timepoint"] if tick else st.segment
                st.day_offset = tick[0]["dayOffset"] if tick else st.day_offset
                st._stop.wait(interval)
            if not st._stop.is_set():
                try:
                    client.post(f"{self.backend_url}/api/replay/finished", json={"runId": st.run_id, "sent": st.sent,
                                                                                 "errors": st.errors})
                except httpx.HTTPError as exc:
                    log.warning("finish callback failed: %s", exc)
        st.running = False
