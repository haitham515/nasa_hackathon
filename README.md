# ASTRA Flight Surgeon

Real-time, 24/7 biomedical telemetry dashboard for **one astronaut**, with clickable threshold alerts,
an AI diagnostic panel and PDF incident reports. NASA Space Apps Challenge 2026,
*Create Health Monitoring Software for Astronauts on Space Missions*.

> **All telemetry is SIMULATED** for a fictional astronaut. Thresholds are illustrative reference ranges.
> AI output is decision support for a flight surgeon, **not a diagnosis**.

## Features

1. **Single-astronaut mission header**:
   - profile, Mission Elapsed Time and UTC clock;
   - live link status (`24/7 ACTIVE` / `PAUSED` / `SIGNAL LOST` / `LINK LOST`);
   - a Pause / Go live toggle and the **Test anomaly** tool.
2. **Live telemetry graphs** for heart rate and three biomarkers (CRP, fibrinogen, hs-troponin I).
   - Each graph shows a normal-range band and dashed Attention and Review limit lines.
   - The **Main graph** picker (or the ⤢ button on a small graph) chooses which metric is shown large at the top.
3. **Clickable anomaly markers**: every out-of-range sample is drawn as a ▲ (Attention) or a ◆ (Review required).
   Click one, or its chip under the graph, to open it in the diagnostic panel.
4. **Right-hand AI diagnostic panel**. In standby it shows a live summary, ongoing anomalies and recent anomalies.
   When an event is selected it shows:
   - the value against its limit, the severity and timestamps;
   - the surrounding trend;
   - the AI analysis: causes in microgravity, immediate actions, countermeasures, escalation criteria.
5. **PDF medical incident report**, generated in the browser with jsPDF.

## Why the data is simulated

No public dataset follows one astronaut's heart rate and blood biomarkers continuously through a space mission.
The closest real cardiovascular data we found is NASA OSDR **OSD-575** (SpaceX Inspiration4). It has only
7 blood draws per crew member, all before launch or after landing, and none during the flight.
That is not enough to drive a 24/7 monitor, so the dashboard **generates** the signals for a fictional astronaut:

- **Baselines and normal ranges** are typical adult reference values (see *Thresholds* below), plus small random
  variation and a daily rhythm.
- **Anomaly types, their possible causes in microgravity, and the recommended countermeasures** are inspired by
  these NASA articles (they describe the risks; they do not contain numerical data series):
  - [Risk of Cardiovascular Adaptations](https://www.nasa.gov/reference/risk-of-cardiovascular-adaptations/)
  - [Cardiovascular Health in Microgravity](https://www.nasa.gov/missions/station/cardiovascular-health-in-microgravity/)
  - [Station Science 101: Cardiovascular Research on Station](https://www.nasa.gov/general/station-science-101-cardiovascular-research-on-station/)
  - [How do dangerous blood clots form in space?](https://science.nasa.gov/science-research/science-enabling-technology/how-do-dangerous-blood-clots-form-in-space/)

Every value on screen and in the PDF is labelled **SIMULATED**.

### Update rates

| Signal | Update interval | Graph window |
|---|---|---|
| Heart rate | every **1 second** | last 5 minutes |
| CRP, fibrinogen, hs-troponin I | every **5 seconds** | last 15 minutes |

Real blood assays take hours. The 5-second biomarker cadence stands in for a hypothetical in-situ biosensor and is
accelerated, so changes are visible during a demo.

## Simulating an anomaly

Anomalies are simulated in two ways:

- **Automatically**: random episodes (tachycardia, bradycardia, CRP rise, fibrinogen rise, troponin elevation)
  start on their own, about every 2.5 minutes (`STREAM_EPISODE_MEAN_S`).
- **On demand**:
  1. In the header's **Test anomaly** box, choose the biomarker or heart-rate anomaly you are interested in.
  2. Press **⚡ Inject**.
  3. Within 10–60 seconds the values cross the limits and ▲ / ◆ markers appear on that graph.
  4. Click a marker. The panel shows the event, and the AI analyses it and recommends ways to tackle it.
  5. Press **Download PDF Report** to save the incident report.

**Pause / Go live** stops and restarts the stream for testing. Pausing closes any ongoing anomaly episode.

## Thresholds

| Metric | Normal range (outside = **Attention**) | **Review required** |
|---|---|---|
| Heart rate | 50–100 bpm | < 40 or > 130 bpm |
| C-reactive protein (CRP) | ≤ 3 mg/L | > 10 mg/L |
| Fibrinogen | 2.0–4.0 g/L | < 1.5 or > 5.0 g/L |
| hs-Troponin I | ≤ 26 ng/L | > 52 ng/L |

These are typical adult reference ranges used for illustration. An **anomaly episode** opens on the first
out-of-range sample, escalates if the severity rises, and closes after 3 normal samples in a row.
Thresholds live in the `telemetry_metric` table (`backend/src/main/resources/db/migration/V3__telemetry.sql`).

## AI analysis (OpenAI)

- **AI analysis requires an OpenAI API key with available credits.** With a valid key, every anomaly is analysed
  by **`gpt-5.4-mini`** (change it with `OPENAI_MODEL`).
- The model returns a structured analysis:
  - summary and clinical interpretation;
  - why this severity applies;
  - possible causes in microgravity;
  - immediate actions for the astronaut;
  - countermeasures;
  - flight-surgeon notes;
  - monitoring plan;
  - escalation criteria.
- **Without a key, or when the account has no credits**, a built-in rule-based knowledge base (from the NASA articles
  above) answers instead. The panel shows **"Rule-based fallback"** and the reason, for example
  `OpenAI account has no credits left (insufficient_quota)`. A failed AI attempt is retried after 2 minutes, so
  analyses switch to OpenAI once credits are added.
- **What is sent to OpenAI**: only the anomaly's numbers, the recent trend and the other current readings.
  No personal data is sent.
- **Safety rules in the prompt**:
  - only NASA-documented, non-drug countermeasures;
  - no medication or dosing;
  - Review-required events always escalate to the flight surgeon.
- One analysis is cached per anomaly episode, and regenerated if the episode escalates.

## Run with Docker

1. Create your settings file (`.env` is git-ignored):

   ```bash
   cp .env.example .env
   ```

2. Edit `.env` and set `OPENAI_API_KEY=` to your key (with credits). Never put the key in source files.
3. Start everything:

   ```bash
   docker compose up --build
   ```

4. Open http://localhost:3000. The stream starts on its own.

After changing the key later, restart only the AI service:

```bash
docker compose up -d ai-service
```

> **Database password:** PostgreSQL applies `POSTGRES_USER` / `POSTGRES_PASSWORD` only when its volume is first
> created. Leave them commented in `.env` unless you start from a fresh volume (`docker compose down -v` deletes the data).

## Run locally without Docker

You need Python 3.11, Java 21+ and Node 20+. Use three terminals.

```bash
cd ai-service && python -m venv .venv && .venv/Scripts/pip install -r requirements.txt && .venv/Scripts/python -m uvicorn app.main:app --port 8000
```

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
cd frontend && npm install && npm run dev
```

Then open http://localhost:5173. `.env` is not read in this mode: set the key in the AI-service terminal first
(PowerShell: `$env:OPENAI_API_KEY="..."`).

## Configuration

| Variable | Where | Default | Meaning |
|---|---|---|---|
| `OPENAI_API_KEY` | ai-service | empty | OpenAI key **with credits**; the rule-based fallback is used when empty or failing |
| `OPENAI_MODEL` | ai-service | `gpt-5.4-mini` | Model used for analysis |
| `STREAM_EPISODE_MEAN_S` | ai-service | `150` | Average seconds between random anomaly episodes |
| `STREAM_AUTOSTART` | ai-service | `true` | Start the 24/7 generator on boot |
| `astra.telemetry.retention-hours` | backend | `24` | Samples older than this, and not part of an anomaly, are purged |

## Architecture

```
ai-service (Python)            backend (Spring Boot)                     frontend (React)
24/7 generator ──POST /api/telemetry/ingest──► thresholds → episodes ──SSE──► live graphs + markers
OpenAI / rule-based ◄──POST /analysis──────── analysis cache (DB)   ◄──REST──  diagnostic panel → PDF
                                               PostgreSQL
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
- [API specification](docs/api-specification.md)
- [Database schema](docs/database-schema.md)
- [Archived Inspiration4 replay](docs/archive-inspiration4.md)
