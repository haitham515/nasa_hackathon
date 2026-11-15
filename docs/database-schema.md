# Database schema

Owned by Flyway: `backend/src/main/resources/db/migration/`
(`V1__schema.sql` tables, `V2__seed.sql` crew + guidance). Hibernate only validates.

| Table | Purpose | Key columns |
|---|---|---|
| `participant` | Crew (coded IDs) | `id` (C001…), `sex` |
| `analysis_run` | History of replays/analyses | `started_at`, `finished_at`, `speed`, `simulate`, `status`, `readings_count`, `alerts_count` |
| `reading` | One biomarker panel or one vitals reading | `participant_id`, **`source` (NASA / SIMULATED)**, `kind` (BIOMARKER_PANEL / VITALS), `timepoint`, `mission_phase`, `day_offset`, `hr_bpm`, `hrv_rmssd_ms` |
| `biomarker_measurement` | 9 rows per panel | `reading_id`, `analyte_key`, `nasa_label`, `concentration`, `unit`, `percent_of_crew_preflight_mean`, `at_assay_upper_limit` |
| `anomaly_result` | Model output per reading | `reading_id` (unique), `model`, `anomaly_score`, `is_atypical`, `n_strong_deviations`, `status`, `details_json`, `data_quality_notes` |
| `alert` | Software notification | `reading_id`, `participant_id`, `source`, `stream` (BIOMARKERS / VITALS), `status`, `category` (PHYSIOLOGY / DATA_QUALITY), `title`, `message`, `guidance_code`, `acknowledged` |
| `guidance` | Fixed NASA-sourced “what to do” texts | `code`, `title`, `body`, `source_name`, `source_url` |

Integrity rules:
- `reading.source` is constrained to `NASA` or `SIMULATED`; exports never mix them.
- Deleting a reading cascades to its measurements, result and alerts (used by Reset).
