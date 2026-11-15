"""24/7 telemetry stream: generates SIMULATED samples every second and POSTs them
to the backend's ingestion endpoint, exactly as a sensor gateway would."""
from __future__ import annotations

import logging
import os
import threading
import time
from collections import deque
from datetime import datetime, timezone

import httpx

from app import config
from app.telemetry.generator import METRICS, SignalGenerator
from app.telemetry.scenarios import ScenarioEngine

log = logging.getLogger("astra.stream")

TICK_S = 1.0
MAX_BUFFER = 600  # samples kept while the backend is unreachable


def _iso(t: float) -> str:
    return datetime.fromtimestamp(t, tz=timezone.utc).isoformat(timespec="milliseconds")


class TelemetryStream:
    def __init__(self, backend_url: str = config.BACKEND_URL, seed: int | None = None,
                 episode_mean_s: float = float(os.getenv("STREAM_EPISODE_MEAN_S", "150"))):
        self.backend_url = backend_url.rstrip("/")
        self.generator = SignalGenerator(seed)
        self.scenarios = ScenarioEngine(self.generator.rng, episode_mean_s)
        self.buffer: deque[dict] = deque(maxlen=MAX_BUFFER)
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self.paused = False
        self.tick = 0
        self.sent = 0
        self.errors = 0
        self.last_error: str | None = None
        self.backend_ok = False
        self.started_at: float | None = None
        self.last_values: dict[str, float] = {}

    # ---- lifecycle ----
    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self.started_at = time.time()
        self._thread = threading.Thread(target=self._run, name="telemetry-stream", daemon=True)
        self._thread.start()
        log.info("telemetry stream started -> %s", self.backend_url)

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=5)

    def pause(self) -> dict:
        self.paused = True
        return self.status()

    def resume(self) -> dict:
        self.paused = False
        return self.status()

    def inject(self, metric: str) -> dict:
        with self._lock:
            ep = self.scenarios.inject(metric, time.time())
        return {"injected": ep.public(time.time()), **self.status()}

    # ---- generation ----
    def generate(self, t: float) -> list[dict]:
        """Samples due at time t (one tick). Public for tests."""
        with self._lock:
            offsets = self.scenarios.offsets(t)
            samples = []
            for metric in METRICS:
                if SignalGenerator.due(metric, self.tick):
                    v = self.generator.sample(metric, t, offsets.get(metric, 0.0))
                    self.last_values[metric] = v
                    samples.append({"metric": metric, "value": v, "recordedAt": _iso(t)})
            self.tick += 1
            return samples

    def _run(self) -> None:
        next_t = time.time()
        with httpx.Client(timeout=5) as client:
            while not self._stop.is_set():
                now = time.time()
                if not self.paused:
                    self.buffer.extend(self.generate(now))
                    self._flush(client)
                next_t += TICK_S
                self._stop.wait(max(0.0, next_t - time.time()))
                if time.time() - next_t > 5:  # fell far behind (e.g. machine sleep): resync
                    next_t = time.time()

    def _flush(self, client: httpx.Client) -> None:
        if not self.buffer:
            return
        batch = list(self.buffer)
        try:
            client.post(f"{self.backend_url}/api/telemetry/ingest", json={"samples": batch}).raise_for_status()
            self.buffer.clear()
            self.sent += len(batch)
            self.backend_ok = True
        except httpx.HTTPError as exc:
            self.errors += 1
            self.backend_ok = False
            self.last_error = str(exc)
            if self.errors % 30 == 1:
                log.warning("ingest failed (%s buffered): %s", len(self.buffer), exc)

    def status(self) -> dict:
        t = time.time()
        return {
            "running": bool(self._thread and self._thread.is_alive()),
            "paused": self.paused,
            "source": "SIMULATED",
            "started_at": _iso(self.started_at) if self.started_at else None,
            "uptime_s": round(t - self.started_at) if self.started_at else 0,
            "sent": self.sent,
            "buffered": len(self.buffer),
            "errors": self.errors,
            "backend_ok": self.backend_ok,
            "last_error": self.last_error,
            "active_episodes": [e.public(t) for e in self.scenarios.active if not e.finished(t)],
            "next_random_episode_in_s": round(self.scenarios.next_at - t) if self.scenarios.next_at else None,
            "last_values": dict(self.last_values),
        }
