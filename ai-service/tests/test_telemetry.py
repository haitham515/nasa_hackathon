"""Tests for the 24/7 stream generator, scenarios and the AI analysis fallback."""
from __future__ import annotations

from unittest import mock

import pytest

from app.analysis import fallback, openai_provider, service
from app.analysis.schema import ClinicalAnalysis
from app.telemetry.generator import METRICS, SignalGenerator
from app.telemetry.scenarios import SCENARIOS, ScenarioEngine
from app.telemetry.stream import TelemetryStream

T0 = 1_800_000_000.0


def run_ticks(stream: TelemetryStream, seconds: int) -> list[dict]:
    out = []
    for i in range(seconds):
        out.extend(stream.generate(T0 + i))
    return out


def test_generator_is_deterministic_with_seed():
    a, b = TelemetryStream(seed=1, episode_mean_s=0), TelemetryStream(seed=1, episode_mean_s=0)
    assert run_ticks(a, 30) == run_ticks(b, 30)


def test_cadence_heart_rate_every_second_biomarkers_every_five():
    samples = run_ticks(TelemetryStream(seed=2, episode_mean_s=0), 10)
    counts = {m: sum(s["metric"] == m for s in samples) for m in METRICS}
    assert counts["heart_rate"] == 10
    assert counts["crp"] == counts["fibrinogen"] == counts["troponin_i"] == 2


def test_baseline_stays_in_normal_range_without_episodes():
    samples = run_ticks(TelemetryStream(seed=3, episode_mean_s=0), 600)
    hr = [s["value"] for s in samples if s["metric"] == "heart_rate"]
    crp = [s["value"] for s in samples if s["metric"] == "crp"]
    assert 50 <= min(hr) and max(hr) <= 100
    assert max(crp) <= 3


@pytest.mark.parametrize("metric,check", [
    ("heart_rate", lambda v: v > 130),
    ("heart_rate_low", lambda v: v < 45),
    ("crp", lambda v: v > 10),
    ("fibrinogen", lambda v: v > 4.5),
    ("troponin_i", lambda v: v > 52),
])
def test_injected_episode_crosses_limits(metric, check):
    stream = TelemetryStream(seed=4, episode_mean_s=0)
    target = "heart_rate" if metric.startswith("heart_rate") else metric
    with mock.patch("app.telemetry.stream.time.time", return_value=T0):
        stream.inject(metric)
    values = [s["value"] for s in run_ticks(stream, 120) if s["metric"] == target]
    assert any(check(v) for v in values)


def test_episode_shape_returns_to_zero():
    eng = ScenarioEngine(SignalGenerator(5).rng, mean_interval_s=0)
    ep = eng.start("tachycardia", T0, intensity=1.0)
    assert ep.offset(T0 - 1) == 0
    assert ep.offset(T0 + ep.duration / 2) == pytest.approx(SCENARIOS["tachycardia"].offset_range[1])
    assert ep.offset(T0 + ep.duration + 1) == 0


EVENT_REQ = {
    "astronaut": {"id": "AST-01"},
    "event": {"metric": "heart_rate", "label": "Heart rate", "unit": "bpm", "severity": "REVIEW_REQUIRED",
              "direction": "HIGH", "breached_limit": 130, "peak_value": 141},
    "selected_sample": {"value": 138, "recorded_at": "2026-11-15T10:00:00Z", "status": "REVIEW_REQUIRED"},
    "trend": {"window_seconds": 180, "min": 66, "max": 141, "mean": 98, "slope_per_minute": 12.5},
    "other_metrics": [],
}


def test_fallback_produces_valid_analysis_with_escalation():
    a = fallback.analyze(EVENT_REQ)
    assert isinstance(a, ClinicalAnalysis)
    assert "141" in a.summary and "130" in a.summary  # summary describes the episode peak
    assert any("flight surgeon" in s.step.lower() for s in a.immediate_actions)
    assert "SIMULATED" in a.disclaimer


@pytest.mark.parametrize("metric,direction", [("crp", "HIGH"), ("fibrinogen", "LOW"), ("troponin_i", "HIGH"),
                                              ("heart_rate", "LOW")])
def test_fallback_covers_every_metric(metric, direction):
    req = {**EVENT_REQ, "event": {**EVENT_REQ["event"], "metric": metric, "direction": direction,
                                  "severity": "ATTENTION"}}
    a = fallback.analyze(req)
    assert a.likely_causes and a.immediate_actions and a.escalation_criteria


def test_service_falls_back_without_api_key(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    out = service.analyze(EVENT_REQ)
    assert out["provider"] == "rule-based"
    assert "OPENAI_API_KEY" in out["fallback_reason"]


def test_service_uses_openai_when_available(monkeypatch):
    fake = fallback.analyze(EVENT_REQ)
    monkeypatch.setattr(openai_provider, "analyze", lambda req: fake)
    out = service.analyze(EVENT_REQ)
    assert out["provider"] == "openai" and out["analysis"]["summary"] == fake.summary


def test_service_falls_back_on_provider_error(monkeypatch):
    def boom(req):
        raise openai_provider.ProviderUnavailable("OpenAI rejected the API key")
    monkeypatch.setattr(openai_provider, "analyze", boom)
    out = service.analyze(EVENT_REQ)
    assert out["provider"] == "rule-based" and "rejected" in out["fallback_reason"]
