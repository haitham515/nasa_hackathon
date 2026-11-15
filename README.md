# ASTRA CARDIO

AI-assisted cardiovascular monitoring prototype for astronauts. NASA Space Apps Challenge 2026,
*Create Health Monitoring Software for Astronauts on Space Missions*.

- **Real data**: NASA OSDR **OSD-575** (SpaceX Inspiration4) serum cardiovascular panel:
  9 biomarkers, 4 crew, 7 timepoints.
- **Simulated data**: a heart-rate / HRV stream, clearly labeled `SIMULATED` everywhere,
  that shows the real-time pipeline.
- **AI**: Isolation Forest comparing each astronaut with their own pre-flight baseline.
  It runs locally, with no ground link needed.

> Exploratory prototype. Notifications are software prompts, **not diagnoses** or medical advice.

## Features

1. **Live crew status board**: NORMAL / ATTENTION / REVIEW REQUIRED per astronaut, with biomarkers (NASA) and vitals (SIMULATED) shown separately
2. **Biomarker and vitals timeline**: fold change vs personal baseline on a log scale, flagged samples ringed, plus a table view
3. **AI anomaly detection**: anomaly score and the biomarkers driving each result
4. **Alerts with "what to do"**: guidance paraphrased from NASA pages, acknowledgement
5. **Data and limits drawer**: sources, method, honest evaluation, separate CSV exports for real and simulated data

## Stack

| Part | Tech | Folder |
|---|---|---|
| Frontend | React 19 + Vite + Recharts | `frontend/` |
| Backend | Spring Boot 4.1 (Java 21), JPA, Flyway, SSE | `backend/` |
| AI service | Python 3.11, FastAPI, pandas, scikit-learn | `ai-service/` |
| Database | PostgreSQL 16 (H2 for local dev) | migrations in `backend/src/main/resources/db/migration` |
| Data | NASA files + processed outputs | `data/` |

## Run with Docker

```bash
docker compose up --build
```

Open http://localhost:3000 and press **Start replay**.

## Run locally without Docker

You need Python 3.11, Java 21+ and Node 20+. Use three terminals.

```bash
cd ai-service
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt     # macOS/Linux: .venv/bin/pip
.venv/Scripts/python -m uvicorn app.main:app --port 8000
```

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # in-memory H2, no PostgreSQL needed
```

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

## Tests

```bash
cd ai-service && .venv/Scripts/python -m pytest -q
```

```bash
cd backend && ./mvnw test
```

## Docs

- [Architecture](docs/architecture.md)
- [Database schema](docs/database-schema.md)
- [API specification](docs/api-specification.md)
- [Method, results and limits](docs/method-and-limits.md)
- [30-second demo script](docs/demo-script.md)
- [Data sources](data/metadata/sources.md) and [variable dictionary](data/metadata/variable_dictionary.csv)

## Before submitting

- Check that OSD-575 appears in the challenge's **Resources** tab (eligibility).
