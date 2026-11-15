# Database schema

Owned by Flyway (`backend/src/main/resources/db/migration/`); Hibernate only validates.

## Flight Surgeon mode (`V3__telemetry.sql`)

| Table | Purpose | Key columns |
|---|---|---|
| `astronaut` | Exactly one row: the monitored (fictional) astronaut | `id` AST-01, `display_name`, `role`, `mission`, `mission_start` |
| `telemetry_metric` | Metric definitions and thresholds | `metric_key`, `unit`, `normal_low/high`, `review_low/high`, `display_min/max`, `window_seconds` |
| `telemetry_sample` | Every streamed value | `metric_key`, `metric_value`, `recorded_at`, `status`, `event_id`, `source` = SIMULATED |
| `anomaly_event` | One breach episode | `metric_key`, `direction`, `worst_status`, `breached_limit`, `peak_value/at`, `started_at`, `ended_at`, `is_open` |
| `ai_analysis` | One cached analysis per event | `event_id` (unique), `severity`, `provider`, `model`, `fallback_reason`, `payload_json` |

Default thresholds (typical adult ranges, illustrative):

| Metric | Normal (outside = Attention) | Review required |
|---|---|---|
| Heart rate | 50–100 bpm | < 40 or > 130 bpm |
| CRP | ≤ 3 mg/L | > 10 mg/L |
| Fibrinogen | 2.0–4.0 g/L | < 1.5 or > 5.0 g/L |
| hs-Troponin I | ≤ 26 ng/L | > 52 ng/L |

## Archived Inspiration4 replay (`V1`, `V2`)

`participant`, `analysis_run`, `reading`, `biomarker_measurement`, `anomaly_result`, `alert`, `guidance`.
These are kept for the archived mode and are unused by the dashboard.
