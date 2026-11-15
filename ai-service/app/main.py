"""ASTRA AI service (FastAPI): anomaly scoring + replay/simulation engine.

Runs locally on board: no ground link is needed to score a reading.
"""
from __future__ import annotations

import json
import logging

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from app import config
from app.models import biomarker_model, vitals_model
from app.replay.engine import ReplayEngine

logging.basicConfig(level=logging.INFO)
app = FastAPI(title="ASTRA AI service", version="1.0.0")
engine = ReplayEngine()


class BiomarkerScoreRequest(BaseModel):
    participant_id: str
    timepoint: str
    sample_name: str | None = None
    percents: dict[str, float] = Field(description="biomarker key -> percent of crew pre-flight mean")


class VitalsScoreRequest(BaseModel):
    participant_id: str
    hr_bpm: float = Field(gt=0, lt=300)
    hrv_rmssd_ms: float = Field(gt=0, lt=1000)


class ReplayStartRequest(BaseModel):
    run_id: int | None = None
    speed: float = Field(default=1.0, gt=0, le=20)
    simulate: bool = True


@app.on_event("startup")
def _warm_up() -> None:
    biomarker_model.load()
    vitals_model.load()


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "biomarker_model": config.BIOMARKER_MODEL_FILE.exists(),
            "vitals_model": config.VITALS_MODEL_FILE.exists()}


@app.post("/train")
def train() -> dict:
    card = biomarker_model.train()
    vitals_model.train()
    biomarker_model.reset_cache()
    vitals_model.reset_cache()
    return card["evaluation"]


@app.get("/model/card")
def model_card() -> dict:
    biomarker_model.load()
    return json.loads(config.MODEL_CARD_FILE.read_text(encoding="utf-8"))


@app.post("/score/biomarkers")
def score_biomarkers(req: BiomarkerScoreRequest) -> dict:
    try:
        return biomarker_model.score(req.participant_id, req.timepoint, req.percents, req.sample_name)
    except (KeyError, ValueError) as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.post("/score/vitals")
def score_vitals(req: VitalsScoreRequest) -> dict:
    try:
        return vitals_model.score(req.participant_id, req.hr_bpm, req.hrv_rmssd_ms)
    except KeyError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.post("/replay/start")
def replay_start(req: ReplayStartRequest) -> dict:
    return engine.start(req.run_id, req.speed, req.simulate)


@app.post("/replay/stop")
def replay_stop() -> dict:
    engine.stop()
    return engine.state.public()


@app.get("/replay/status")
def replay_status() -> dict:
    return engine.state.public()
