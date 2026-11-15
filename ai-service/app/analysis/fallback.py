"""Rule-based analysis built from the knowledge base and the event's numbers."""
from __future__ import annotations

from app.analysis import knowledge
from app.analysis.schema import Cause, ClinicalAnalysis, Step


def _fmt(v) -> str:
    if v is None:
        return "—"
    return f"{v:g}" if isinstance(v, (int, float)) else str(v)


def analyze(req: dict) -> ClinicalAnalysis:
    ev = req.get("event", {})
    sel = req.get("selected_sample") or {}
    trend = req.get("trend", {})
    metric, direction = ev.get("metric", ""), ev.get("direction", "HIGH")
    severity = ev.get("severity", "ATTENTION")
    review = severity == "REVIEW_REQUIRED"
    kb = knowledge.lookup(metric, direction)

    value = ev.get("peak_value", sel.get("value"))
    unit = ev.get("unit", "")
    limit = ev.get("breached_limit")
    side = "above" if direction == "HIGH" else "below"
    slope = trend.get("slope_per_minute", 0) or 0
    trend_word = "rising" if slope > 0.05 else "falling" if slope < -0.05 else "stable"

    immediate = [Step(step=s, rationale=r) for s, r in kb["immediate"]]
    if review:
        s, r = kb["review_extra"]
        immediate.append(Step(step=s, rationale=r))

    sources = "; ".join(f"{knowledge.SOURCES[k][0]} ({knowledge.SOURCES[k][1]})" for k in kb["sources"])
    return ClinicalAnalysis(
        summary=(f"{ev.get('label', metric)} peaked at {_fmt(value)} {unit}, {side} the {_fmt(limit)} {unit} limit "
                 f"({'Review required' if review else 'Attention'}). The trend around the peak is {trend_word}."),
        clinical_interpretation=kb["interpretation"],
        severity_rationale=(f"The value crossed the {'review' if review else 'normal-range'} limit of {_fmt(limit)} {unit}. "
                            + ("Review-level breaches go to the flight surgeon." if review
                               else "Attention-level breaches call for self-checks and continued monitoring.")),
        likely_causes=[Cause(cause=c, microgravity_mechanism=m, likelihood=lk) for c, m, lk in kb["causes"]],
        immediate_actions=immediate,
        countermeasures=[Step(step=s, rationale=r) for s, r in kb["countermeasures"]],
        flight_surgeon_notes=(f"Trend over the last {trend.get('window_seconds', '?')} s: min {_fmt(trend.get('min'))}, "
                              f"max {_fmt(trend.get('max'))}, mean {_fmt(trend.get('mean'))} {unit}, slope "
                              f"{slope:+.2f} {unit}/min. Correlate with the other metrics and the crew timeline. Sources: {sources}."),
        monitoring_plan=("Continuous monitoring; re-assess when the value returns to range or at the next sample. "
                         + ("Keep the flight surgeon informed until the event closes." if review else "")).strip(),
        escalation_criteria=kb["escalation"],
        confidence="medium" if review else "low",
        disclaimer=knowledge.DISCLAIMER,
    )
