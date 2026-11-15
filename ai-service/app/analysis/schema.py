"""Structured output of the clinical analysis (shared by the OpenAI provider and the fallback)."""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class Cause(BaseModel):
    cause: str = Field(description="Possible cause, plain language")
    microgravity_mechanism: str = Field(description="How spaceflight / microgravity can contribute")
    likelihood: Literal["low", "moderate", "high"]


class Step(BaseModel):
    step: str = Field(description="One concrete action, imperative voice")
    rationale: str = Field(description="Why this step, one sentence")


class ClinicalAnalysis(BaseModel):
    summary: str = Field(description="Two sentences: what happened and how serious it is")
    clinical_interpretation: str
    severity_rationale: str = Field(description="Why this is ATTENTION or REVIEW REQUIRED")
    likely_causes: list[Cause]
    immediate_actions: list[Step] = Field(description="Ordered steps for the astronaut, next 15 minutes")
    countermeasures: list[Step] = Field(description="Protocol / countermeasures for the following hours")
    flight_surgeon_notes: str = Field(description="What the flight surgeon should check or decide")
    monitoring_plan: str
    escalation_criteria: list[str] = Field(description="Signs that require immediate escalation")
    confidence: Literal["low", "medium", "high"]
    disclaimer: str
