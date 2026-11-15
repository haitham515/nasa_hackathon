"""Tests for preprocessing, biomarker scoring, simulated vitals and replay events."""
from __future__ import annotations

import pandas as pd
import pytest

from app import config
from app.models import biomarker_model, vitals_model
from app.preprocessing import osd575
from app.replay.engine import build_events


@pytest.fixture(scope="module", autouse=True)
def trained():
    biomarker_model.train()
    vitals_model.train()
    biomarker_model.reset_cache()
    vitals_model.reset_cache()


def test_raw_file_is_validated_and_complete():
    raw = osd575.load_raw()
    report = osd575.validate(raw)
    assert report["rows"] == 252  # 9 biomarkers x 4 participants x 7 timepoints
    assert report["participants"] == ["C001", "C002", "C003", "C004"]
    assert report["duplicate_keys"] == 0
    assert report["blocking_problems"] == []


def test_processed_wide_has_28_samples_and_keeps_identifiers():
    wide = pd.read_csv(config.PROCESSED_WIDE_FILE)
    assert len(wide) == 28
    for col in ["participant_id", "sample_name", "timepoint", "phase"]:
        assert col in wide.columns
    assert set(config.FEATURES).issubset(wide.columns)


def test_identifiers_are_not_model_features():
    art = biomarker_model.load()
    assert art["features"] == config.FEATURES
    assert "participant_id" not in art["features"] and "timepoint" not in art["features"]


def test_streaming_score_matches_batch_results():
    wide = pd.read_csv(config.PROCESSED_WIDE_FILE)
    batch = pd.read_csv(config.ANOMALY_RESULTS_FILE).set_index("sample_name")
    for _, row in wide.iterrows():
        res = biomarker_model.score(row["participant_id"], row["timepoint"], row[config.FEATURES].to_dict())
        assert res["is_atypical"] == bool(batch.loc[row["sample_name"], "is_atypical"])
        assert res["anomaly_score"] == pytest.approx(batch.loc[row["sample_name"], "anomaly_score"], abs=1e-3)


def test_l44_is_reported_as_crew_wide_shift():
    wide = pd.read_csv(config.PROCESSED_WIDE_FILE)
    row = wide[(wide.participant_id == "C003") & (wide.timepoint == "L-44")].iloc[0]
    res = biomarker_model.score("C003", "L-44", row[config.FEATURES].to_dict())
    assert any("Crew-wide shift" in n for n in res["data_quality_notes"])


def test_unknown_participant_is_rejected():
    with pytest.raises(KeyError):
        biomarker_model.score("C999", "R+1", {k: 100.0 for k in config.FEATURES})


def test_simulated_vitals_flag_large_heart_rate_rise():
    base = vitals_model.load()["baselines"]["C001"]
    calm = vitals_model.score("C001", base["hr"], base["hrv"])
    high = vitals_model.score("C001", base["hr"] + 20, base["hrv"] * 0.6)
    assert calm["is_atypical"] is False
    assert high["is_atypical"] is True
    assert high["source"] == "SIMULATED"


def test_replay_events_label_sources_and_are_deterministic():
    a, b = build_events(True), build_events(True)
    assert a == b
    flat = [r for tick in a for r in tick]
    panels = [r for r in flat if r["kind"] == "BIOMARKER_PANEL"]
    vitals = [r for r in flat if r["kind"] == "VITALS"]
    assert len(panels) == 28 and all(r["source"] == "NASA" for r in panels)
    assert vitals and all(r["source"] == "SIMULATED" for r in vitals)
    assert not [r for r in flat if r["kind"] == "VITALS" and r["source"] == "NASA"]


def test_replay_without_simulation_sends_only_nasa():
    flat = [r for tick in build_events(False) for r in tick]
    assert len(flat) == 28 and all(r["source"] == "NASA" for r in flat)
