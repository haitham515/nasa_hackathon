"""Entry point: try the AI provider, fall back to the rule-based analysis on any problem."""
from __future__ import annotations

import logging

from app.analysis import fallback, openai_provider

log = logging.getLogger("astra.analysis")


def analyze(req: dict) -> dict:
    try:
        result = openai_provider.analyze(req)
        return {"provider": "openai", "model": openai_provider.model_name(), "fallback_reason": None,
                "analysis": result.model_dump()}
    except openai_provider.ProviderUnavailable as exc:
        reason = str(exc)
    except Exception as exc:  # never let the AI path break the dashboard
        log.exception("AI analysis failed")
        reason = f"Unexpected AI error: {type(exc).__name__}"
    return {"provider": "rule-based", "model": None, "fallback_reason": reason,
            "analysis": fallback.analyze(req).model_dump()}
