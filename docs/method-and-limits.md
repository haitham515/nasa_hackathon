# Method, results and limits

## Biomarker model (real NASA data)

1. **Input**: 28 serum samples (4 crew × 7 timepoints), 9 cardiovascular biomarkers, using
   NASA's `percent` column (concentration normalized by the crew pre-flight mean). The raw
   `concentration` is shown but not used as a second, redundant model input.
2. **Personal baseline**: each astronaut's own mean log(percent) at **L-92 and L-3**. A
   baseline sample is compared with the *other* baseline sample only (no leakage).
3. **Why L-44 is excluded from the baseline**: all four crew drop at the same time at L-44
   (CRP 7-12 %, haptoglobin 1-3 % of the crew mean). A simultaneous crew-wide shift is more
   consistent with sample handling or a lab batch than with physiology. L-44 is still scored
   and shown, and the app labels it as a possible data-quality issue. Configurable with
   `ASTRA_BASELINE_TIMEPOINTS`.
4. **Features**: log fold change vs personal baseline, one per biomarker. IDs and
   timepoints are not model inputs.
5. **Model**: `IsolationForest(n_estimators=500, contamination=0.10, random_state=42)`.
   `contamination` is a technical choice (~3 of 28 samples), not a clinical threshold.
6. **Explanation**: per-biomarker z = deviation / std of baseline deviations. |z| ≥ 3 is a
   "strong" deviation.

### Results

| Sample | Result | Driver |
|---|---|---|
| C001 R+82 | atypical, 7 strong deviations | α2-macroglobulin, L-selectin, SAP all up ×2-3 |
| C002 R+1 | atypical, 7 strong deviations | AGP, fetuin-A, L-selectin down |
| C003 L-44 | atypical | crew-wide L-44 shift (data quality) |
| C001 R+194 | not flagged, 4 strong deviations → ATTENTION by rule | haptoglobin ×~50 vs own baseline |

### Honest evaluation

- No independent test set is possible (28 samples, 4 people, repeated measures).
- Leave-one-astronaut-out refits recover **0 of the 3 flags**. Individual flags are not
  stable, so they are prompts to look at the data, not findings.
- Some haptoglobin values (307.64 %) repeat exactly in 7 samples, which looks like an assay
  upper limit. They are marked as censored.

## Simulated vitals

- Heart rate and HRV are **generated** (fixed seed) because no downloadable in-flight wearable
  data for this crew was found.
- Only the *direction* of effects follows the Inspiration4 paper (Nature 2024): C004 lower heart
  rate and higher HRV in flight; C001 and C002 higher heart rate after return. Magnitudes are invented.
- Separate Isolation Forest trained on simulated pre-flight readings (contamination 0.02).

## Notification rules

| Stream | ATTENTION | REVIEW REQUIRED |
|---|---|---|
| Biomarkers | model flag, or ≥ 2 strong deviations | model flag and (≥ 2 strong deviations or previous panel flagged) |
| Vitals (SIMULATED) | atypical reading | 3 atypical readings in a row |

Vitals alerts fire at most once per level per mission phase.

## What this prototype is not

- Not a diagnosis, not a medical device, not validated.
- Not continuous in-flight monitoring of real data: the NASA data are point-in-time serum samples.
- No real heart rate, blood pressure or ECG is used; every such value on screen is SIMULATED.
