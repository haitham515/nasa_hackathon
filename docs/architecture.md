# Architecture

```
                 ┌──────────────────────────── ai-service (Python, FastAPI :8000) ───────────┐
 data/raw ──────►│ preprocessing ─► biomarker Isolation Forest      vitals Isolation Forest │
 (NASA OSD-575)  │                       ▲ /score/biomarkers              ▲ /score/vitals    │
                 │ replay engine ── POST /api/readings ──┐  simulator (SIMULATED HR/HRV)     │
                 └───────────────────────────────────────┼──────────────────────────────────┘
                                                         ▼
 React (Vite :5173 / nginx :3000) ◄── SSE /api/stream ── backend (Spring Boot :8080) ──► PostgreSQL
            │  REST /api/crew, /alerts, /replay …            ingest → score → status → alert
            └────────────────────────────────────────────►
```

## Flow of one reading

1. The **replay engine** (ai-service) sends a reading to `POST /api/readings`, the same
   endpoint a real device integration would use. Biomarker panels come from the NASA
   file; heart-rate readings come from the simulator and are tagged `SIMULATED`.
2. The **backend** stores it, then calls the AI service to score it
   (`/score/biomarkers` or `/score/vitals`).
3. The backend applies the **status rules** (`StatusRules.java`) to turn the score into
   NORMAL / ATTENTION / REVIEW_REQUIRED, and creates an **alert** with a guidance code.
4. After the transaction commits, the backend pushes a `reading` (and `alert`) event over
   **Server-Sent Events**. The dashboard refetches what changed.

## Why this split

- **Python** owns everything statistical (pandas, scikit-learn) and the data replay.
- **Spring Boot** owns persistence, business rules, alerting and the public API.
- **Scoring is local**: no ground link is needed to evaluate a reading, which matches the
  challenge's point that crews must assess their own health on long missions.

## Mission timeline used by the replay

`L-92 → L-44 → L-3 → in flight (days 0-3, vitals only) → R+1 → R+45 → R+82 → R+194`.
At speed ×1 the whole timeline plays in about 25 seconds (47 ticks × 0.5 s).

## Running modes

| Mode | Database | How |
|---|---|---|
| Docker | PostgreSQL 16 | `docker compose up --build` |
| Local dev | H2 in memory (PostgreSQL mode) | backend profile `local`, see README |

The same Flyway migrations run in both modes.
