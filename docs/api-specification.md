# API specification

## Backend (Spring Boot, `:8080`)

| Method | Path | Description |
|---|---|---|
| POST | `/api/readings` | Ingest a reading (used by the replay engine or a device integration) |
| GET | `/api/crew` | Status board: per astronaut biomarker status (NASA) and vitals status (SIMULATED) |
| GET | `/api/crew/{id}/timeline` | Panels (biomarkers, fold change, z, score, status) and vitals series |
| GET | `/api/alerts?participantId=&limit=` | Alerts, newest first |
| GET | `/api/alerts/{id}` | Alert detail: contributions, values, data-quality notes, guidance, disclaimer |
| POST | `/api/alerts/{id}/acknowledge` | Mark as acknowledged |
| GET | `/api/guidance` | Guidance knowledge base |
| POST | `/api/replay/start` | Body `{ "speed": 1, "simulate": true }`. Clears live data, opens a run, starts replay |
| POST | `/api/replay/stop` · `/api/replay/reset` | Stop, or stop and clear |
| POST | `/api/replay/finished` | Callback from the replay engine |
| GET | `/api/replay/status` · `/api/runs` | Engine progress, counts, run history |
| GET | `/api/export/nasa-biomarker-results.csv` | NASA panels + model results |
| GET | `/api/export/simulated-vitals.csv` | SIMULATED vitals (separate file, labeled) |
| GET | `/api/stream` | Server-Sent Events: `reading`, `alert`, `replay`, `ping` |
| GET | `/api/meta` | Dataset description, simulation disclosure, model card |

### `POST /api/readings`

```json
{
  "runId": 1, "source": "NASA", "kind": "BIOMARKER_PANEL",
  "participantId": "C001", "timepoint": "R+82", "missionPhase": "POST_FLIGHT", "dayOffset": 85,
  "sampleName": "C001_serum_R+82", "study": "OSD-575",
  "measurements": [
    { "analyteKey": "crp", "nasaLabel": "CRP", "concentration": 25000000, "unit": "pg/ml",
      "percent": 141.0, "atAssayUpperLimit": false }
  ]
}
```

Vitals: `"source": "SIMULATED", "kind": "VITALS", "hrBpm": 74.2, "hrvRmssdMs": 36.1` (no measurements).

Errors: `422` unknown participant or wrong shape, `400` validation, `503` AI service unavailable.

## AI service (FastAPI, `:8000`, OpenAPI at `/docs`)

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Service and model status |
| POST | `/train` | Re-run preprocessing and retrain both models |
| GET | `/model/card` | Method, parameters, evaluation, limitations |
| POST | `/score/biomarkers` | `{participant_id, timepoint, sample_name?, percents{key: %}}` → score, flag, contributions, notes |
| POST | `/score/vitals` | `{participant_id, hr_bpm, hrv_rmssd_ms}` → score, flag, deltas |
| POST | `/replay/start` · `/replay/stop` | `{run_id, speed, simulate}` |
| GET | `/replay/status` | Tick, segment, sent, errors |
