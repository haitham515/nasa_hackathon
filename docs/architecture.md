# Architecture

```
┌──────────── ai-service (Python, FastAPI :8000) ────────────┐
│ telemetry/generator  baseline + daily rhythm + random walk │
│ telemetry/scenarios  random + injected anomaly episodes    │
│ telemetry/stream     1 s tick → POST /api/telemetry/ingest │──┐
│ analysis/            OpenAI (structured) → rule fallback   │◄─┼─ POST /analysis
└────────────────────────────────────────────────────────────┘  │
                                                                ▼
┌──────────── backend (Spring Boot :8080) ───────────────────────────────┐      ┌──────────┐
│ TelemetryService   ThresholdRules → status → anomaly episodes          │─────►│PostgreSQL│
│ TelemetryQueryService  snapshot, event detail ±90 s, trend statistics  │      └──────────┘
│ AnalysisService    one cached analysis per event (refreshed on escalation)
│ SseBroadcaster     events: telemetry, anomaly, stream, ping            │
└────────────────────────────────────────────────────────────────────────┘
                 │ SSE /api/stream             ▲ REST /api/telemetry/*
                 ▼                             │
┌──────────── frontend (React + Recharts + jsPDF) ─────────────────────────┐
│ useTelemetry: snapshot + live rolling windows (5 min HR, 15 min markers) │
│ TelemetryChart: band, limits, clickable Attention/Review nodes           │
│ DiagnosticPanel: standby summary or selected event + AI analysis → PDF   │
└──────────────────────────────────────────────────────────────────────────┘
```

## Flow of one sample

1. Every second the generator emits heart rate. Every 5 s it also emits CRP, fibrinogen and troponin.
   Values come from the astronaut's baseline plus noise, plus the offset of any active anomaly episode.
2. `POST /api/telemetry/ingest` stores each sample with a status:
   - outside the normal band → `ATTENTION`;
   - beyond a review limit → `REVIEW_REQUIRED`.
3. Breaches are grouped into **anomaly episodes**. The first breach opens one, a higher severity escalates it,
   and 3 normal samples in a row close it.
4. After commit, the backend pushes `telemetry` (samples) and `anomaly` (opened, escalated, closed) events.
   The dashboard appends samples to its rolling windows without refetching.
5. Clicking a marker loads the event detail (±90 s context and trend statistics) and requests the analysis.
   The backend returns the cached analysis, or asks the AI service when there is none or the event escalated.

## AI analysis

- **OpenAI** (`OPENAI_API_KEY` set): `responses.parse` into a Pydantic `ClinicalAnalysis` schema.
  The prompt allows only NASA-documented non-drug countermeasures and no dosing. Review events always escalate
  to the flight surgeon. Only the event numbers, the trend and the other current readings are sent.
- **Fallback**: a deterministic knowledge base keyed by metric and direction, paraphrased from NASA pages.
  It is used when the key is missing or the API fails, and the panel shows which engine answered.

## Robustness

- The generator buffers up to 600 samples while the backend is unreachable, then flushes them.
- The dashboard shows `SIGNAL LOST` when no sample arrives for 6 s, and refetches its snapshot after an SSE reconnect.
- Pausing closes open episodes, so nothing stays "ongoing" forever.
- A retention job drops samples older than 24 h unless they belong to an anomaly episode.

The archived Inspiration4 replay (OSD-575) still lives in the same services; see `archive-inspiration4.md`.
