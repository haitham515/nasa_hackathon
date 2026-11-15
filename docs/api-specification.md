# API specification

## Backend (Spring Boot, `:8080`)

| Method | Path | Description |
|---|---|---|
| GET | `/api/astronaut` | The single astronaut profile, mission start and server time |
| GET | `/api/telemetry/metrics` | Metric definitions and thresholds |
| GET | `/api/telemetry/snapshot` | Profile, metrics, recent window per metric, last 30 events, stream status |
| GET | `/api/telemetry/events?limit=30` | Anomaly episodes, newest first |
| GET | `/api/telemetry/events/{id}?sampleId=` | Event, metric, selected sample, ±90 s context, trend stats, other metrics' latest values |
| POST | `/api/telemetry/events/{id}/analysis` | Cached analysis, or a new one when missing or the event escalated |
| GET | `/api/telemetry/events/{id}/analysis` | Cached analysis only (204 if none) |
| POST | `/api/telemetry/ingest` | `{ "samples": [{ "metric", "value", "recordedAt" }] }` (generator or sensor gateway) |
| GET | `/api/telemetry/stream` | Generator status (running, paused, active episodes, AI provider) |
| POST | `/api/telemetry/stream/pause` · `/resume` | Pause/resume the generator (pause closes open episodes) |
| POST | `/api/telemetry/stream/inject` | `{ "metric": "heart_rate" | "heart_rate_low" | "crp" | "fibrinogen" | "troponin_i" }` |
| GET | `/api/stream` | Server-Sent Events: `telemetry`, `anomaly`, `stream`, `ping` |

Analysis response:

```json
{ "eventId": 12, "severity": "REVIEW_REQUIRED", "provider": "openai", "model": "gpt-5.4-mini",
  "fallbackReason": null, "createdAt": "...",
  "analysis": { "summary": "...", "clinical_interpretation": "...", "severity_rationale": "...",
                "likely_causes": [{ "cause": "...", "microgravity_mechanism": "...", "likelihood": "moderate" }],
                "immediate_actions": [{ "step": "...", "rationale": "..." }], "countermeasures": [...],
                "flight_surgeon_notes": "...", "monitoring_plan": "...", "escalation_criteria": ["..."],
                "confidence": "medium", "disclaimer": "..." } }
```

Errors: `422` unknown metric, `400` validation, `503` AI service unavailable.

## AI service (FastAPI, `:8000`, OpenAPI at `/docs`)

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Status, stream running, AI provider |
| GET | `/stream/status` · POST `/stream/pause` · `/stream/resume` · `/stream/inject` | Generator control |
| POST | `/analysis` | `{ astronaut, mission_elapsed, event, selected_sample, trend, other_metrics }` → `{ provider, model, fallback_reason, analysis }` |

Archived Inspiration4 endpoints (`/api/crew`, `/api/replay/*`, `/score/*`, `/replay/*`) are described in `archive-inspiration4.md`.
