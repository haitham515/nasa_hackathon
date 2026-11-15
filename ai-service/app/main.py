"""ASTRA AI service (FastAPI).

* 24/7 SIMULATED telemetry stream for one astronaut (posts to the backend).
* Clinical analysis of anomaly events (OpenAI when configured, rule-based fallback).
* Archived: OSD-575 Inspiration4 replay and its Isolation Forest models.
"""
from __future__ import annotations

import json
import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from app import config
from app.analysis import openai_provider
from app.analysis import service as analysis_service
from app.models import biomarker_model, vitals_model
from app.replay.engine import ReplayEngine
from app.telemetry.stream import TelemetryStream

logging.basicConfig(level=logging.INFO)

stream = TelemetryStream()
engine = ReplayEngine()  # archived Inspiration4 replay


@asynccontextmanager
async def lifespan(_: FastAPI):
    if os.getenv("STREAM_AUTOSTART", "true").lower() == "true":
        stream.start()
    yield
    stream.stop()


app = FastAPI(title="ASTRA AI service", version="2.0.0", lifespan=lifespan)


# ---------------------------------------------------------------- models

class AnalysisRequest(BaseModel):
    astronaut: dict = Field(default_factory=dict)
    mission_elapsed: str | None = None
    event: dict
    selected_sample: dict = Field(default_factory=dict)
    trend: dict = Field(default_factory=dict)
    other_metrics: list[dict] = Field(default_factory=list)


class InjectRequest(BaseModel):
    metric: str


# ---------------------------------------------------------------- health

@app.get("/health")
def health() -> dict:
    return {"status": "UP", "stream": stream.status()["running"],
            "ai_provider": "openai" if openai_provider.available() else "rule-based",
            "ai_model": openai_provider.model_name() if openai_provider.available() else None}


# ---------------------------------------------------------------- 24/7 stream

@app.get("/stream/status")
def stream_status() -> dict:
    return {**stream.status(), "ai_provider": "openai" if openai_provider.available() else "rule-based",
            "ai_model": openai_provider.model_name() if openai_provider.available() else None}


@app.post("/stream/pause")
def stream_pause() -> dict:
    return stream.pause()


@app.post("/stream/resume")
def stream_resume() -> dict:
    if not stream.status()["running"]:
        stream.start()
    return stream.resume()


@app.post("/stream/inject")
def stream_inject(req: InjectRequest) -> dict:
    try:
        return stream.inject(req.metric)
    except KeyError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


# ---------------------------------------------------------------- AI analysis

@app.post("/analysis")
def analysis(req: AnalysisRequest) -> dict:
    return analysis_service.analyze(req.model_dump())


# ---------------------------------------------------------------- archived: Inspiration4 replay

class BiomarkerScoreRequest(BaseModel):
    participant_id: str
    timepoint: str
    sample_name: str | None = None
    percents: dict[str, float]


class VitalsScoreRequest(BaseModel):
    participant_id: str
    hr_bpm: float = Field(gt=0, lt=300)
    hrv_rmssd_ms: float = Field(gt=0, lt=1000)


class ReplayStartRequest(BaseModel):
    run_id: int | None = None
    speed: float = Field(default=1.0, gt=0, le=20)
    simulate: bool = True


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
