# Archived mode: Inspiration4 crew replay

The first version of this project replayed the **real** NASA OSDR OSD-575 serum cardiovascular panel
(SpaceX Inspiration4: 4 crew, 7 timepoints, 9 biomarkers) as a 25-second story. It is no longer in the UI.

- **Full version:** git tag `archive/inspiration4-replay` (`git checkout archive/inspiration4-replay`).
- **What still runs in the current services** (unused by the dashboard):
  - backend: `/api/crew`, `/api/alerts`, `/api/replay/*`, `/api/export/*`, `/api/meta`;
  - ai-service: `/score/biomarkers`, `/score/vitals`, `/replay/*`, `/train`, `/model/card`;
  - data: `data/raw` (NASA files), `data/processed`, `data/metadata`.
- **Method, results and limits:** `docs/method-and-limits.md`.

Key findings from that work:
- The L-44 samples show a crew-wide batch effect.
- Isolation Forest flagged C001 R+82 and C002 R+1.
- Leave-one-astronaut-out refits recovered none of the flags.
