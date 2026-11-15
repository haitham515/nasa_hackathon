"""OpenAI provider: structured clinical analysis via the official `openai` SDK.

The API key is read from the OPENAI_API_KEY environment variable only; it is never
logged or returned. The model is configurable with OPENAI_MODEL.
"""
from __future__ import annotations

import json
import os

from app.analysis import knowledge
from app.analysis.schema import ClinicalAnalysis

DEFAULT_MODEL = "gpt-5.4-mini"
TIMEOUT_S = float(os.getenv("OPENAI_TIMEOUT_S", "30"))

INSTRUCTIONS = f"""You are a clinical decision-support assistant for a flight surgeon monitoring ONE astronaut
on a long-duration space mission. You receive one threshold-breach event from SIMULATED telemetry
(heart rate or blood biomarkers), its recent trend and the astronaut's other current readings.

Write a concise, practical analysis in the required JSON structure:
- Explain what the value means and why the severity (ATTENTION or REVIEW_REQUIRED) applies.
- List plausible causes, each linked to a known spaceflight mechanism (fluid shift, cardiovascular
  deconditioning, venous stasis and jugular vein clots, immune changes, radiation, sleep and stress,
  exercise countermeasures). Weigh likelihood using the trend and the other readings.
- Give ordered immediate actions the astronaut can take in the next 15 minutes, then countermeasures.
- Stay within NASA-documented, non-pharmacological measures: rest and re-measure, check the sensor,
  hydration, compression garments, exercise adjustment, symptom reporting, ECG / ultrasound per protocol.
- Never prescribe or dose medication. Never state a definitive diagnosis.
- For REVIEW_REQUIRED, the first or second action must be contacting the flight surgeon.
- Keep each field short (1-3 sentences); 3-5 causes, 3-6 actions.
- disclaimer must say the data is simulated and this is not a diagnosis.

Reference guidance you may draw on (paraphrase, do not invent sources):
{json.dumps({f"{m}/{d}": {"interpretation": v["interpretation"], "escalation": v["escalation"]}
             for (m, d), v in knowledge.KB.items()}, indent=0)}
"""


class ProviderUnavailable(Exception):
    """Raised when the provider cannot be used; the caller falls back to rule-based analysis."""


def model_name() -> str:
    return os.getenv("OPENAI_MODEL", DEFAULT_MODEL)


def available() -> bool:
    return bool(os.getenv("OPENAI_API_KEY"))


def analyze(req: dict) -> ClinicalAnalysis:
    if not available():
        raise ProviderUnavailable("OPENAI_API_KEY is not set")
    import openai  # imported lazily so the service runs without the package configured

    client = openai.OpenAI(timeout=TIMEOUT_S, max_retries=1)
    try:
        response = client.responses.parse(
            model=model_name(),
            instructions=INSTRUCTIONS,
            input=json.dumps(req),
            text_format=ClinicalAnalysis,
            max_output_tokens=6000,
        )
    except openai.AuthenticationError as exc:
        raise ProviderUnavailable("OpenAI rejected the API key") from exc
    except openai.RateLimitError as exc:
        body = exc.body if isinstance(exc.body, dict) else {}
        if "insufficient_quota" in (getattr(exc, "code", None), body.get("type"), body.get("code")):
            raise ProviderUnavailable("OpenAI account has no credits left (insufficient_quota); add billing credits") from exc
        raise ProviderUnavailable("OpenAI rate limit reached; retry shortly") from exc
    except openai.NotFoundError as exc:
        raise ProviderUnavailable(f"OpenAI model '{model_name()}' not available") from exc
    except openai.APIConnectionError as exc:
        raise ProviderUnavailable("Cannot reach the OpenAI API") from exc
    except openai.APIStatusError as exc:
        raise ProviderUnavailable(f"OpenAI API error {exc.status_code}") from exc

    parsed = response.output_parsed
    if parsed is None:
        raise ProviderUnavailable("OpenAI returned no parsable analysis (refusal or truncated output)")
    return parsed
