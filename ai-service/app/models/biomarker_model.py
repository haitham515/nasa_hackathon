"""Isolation Forest on serum cardiovascular biomarkers (NASA OSD-575, REAL data).

Method (exploratory proof of concept, NOT a medical device):
  1. Personal baseline: for each astronaut, the mean of log(percent) over the
     baseline timepoints (default L-92 and L-3). A baseline sample is compared
     to the *other* baseline samples only (leave-one-out) to avoid leakage.
  2. Feature = log fold-change vs personal baseline, one per biomarker (9).
     Identifiers and timepoints are NOT model inputs.
  3. Isolation Forest fitted on the 28 samples (unsupervised). `contamination`
     is a technical choice (~3 samples flagged), not a clinical threshold.
  4. Explanation: per-biomarker z = deviation / std of baseline deviations.
  5. Stability check: leave-one-participant-out (LOPO) refit agreement.
"""
from __future__ import annotations

import json
from datetime import datetime, timezone

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest

from app import config
from app.preprocessing import osd575

F = config.FEATURES


def _log(values: pd.DataFrame | pd.Series) -> pd.DataFrame | pd.Series:
    return np.log(values.astype(float))


def build_baselines(wide: pd.DataFrame, baseline_tps: tuple[str, ...]) -> dict[str, dict[str, list[float]]]:
    """participant -> timepoint -> log(percent) vector over F, for baseline timepoints."""
    out: dict[str, dict[str, list[float]]] = {}
    for _, row in wide[wide["timepoint"].isin(baseline_tps)].iterrows():
        out.setdefault(row["participant_id"], {})[row["timepoint"]] = _log(row[F]).tolist()
    return out


def deviation(baselines: dict, participant_id: str, timepoint: str, percents: dict[str, float]) -> np.ndarray:
    if participant_id not in baselines:
        raise KeyError(f"No personal baseline for participant {participant_id}")
    ref = [v for tp, v in baselines[participant_id].items() if tp != timepoint]
    if not ref:
        raise KeyError(f"Baseline for {participant_id} has no sample other than {timepoint}")
    x = np.log(np.array([float(percents[k]) for k in F]))
    return x - np.mean(np.array(ref), axis=0)


def deviation_matrix(wide: pd.DataFrame, baselines: dict) -> pd.DataFrame:
    rows = [
        deviation(baselines, row["participant_id"], row["timepoint"], row[F].to_dict()) for _, row in wide.iterrows()
    ]
    return pd.DataFrame(rows, columns=F, index=wide.index)


def _fit(D: pd.DataFrame) -> IsolationForest:
    return IsolationForest(
        n_estimators=config.IF_N_ESTIMATORS,
        contamination=config.IF_CONTAMINATION,
        random_state=config.RANDOM_STATE,
    ).fit(D.values)


def explain(dev: np.ndarray, ref_std: np.ndarray, percents: dict[str, float], ceiling: set[str]) -> list[dict]:
    z = dev / ref_std
    items = [
        {
            "analyte": k,
            "z": round(float(z[i]), 2),
            "fold_change_vs_baseline": round(float(np.exp(dev[i])), 3),
            "percent_of_crew_preflight_mean": round(float(percents[k]), 2),
            "direction": "above" if dev[i] > 0 else "below",
            "at_assay_upper_limit": k in ceiling,
        }
        for i, k in enumerate(F)
    ]
    return sorted(items, key=lambda d: abs(d["z"]), reverse=True)


def train() -> dict:
    long_df, wide, report = osd575.run()
    baselines = build_baselines(wide, config.BASELINE_TIMEPOINTS)
    D = deviation_matrix(wide, baselines)
    model = _fit(D)

    ref_mask = wide["timepoint"].isin(config.BASELINE_TIMEPOINTS)
    ref_std = D[ref_mask].std(ddof=1).replace(0, np.nan).fillna(1.0).values

    scores = -model.decision_function(D.values)  # > 0 means atypical
    flags = model.predict(D.values) == -1

    # Leave-one-participant-out stability: refit without participant P, score P.
    lopo_flags = np.zeros(len(wide), dtype=bool)
    for pid in wide["participant_id"].unique():
        held = (wide["participant_id"] == pid).values
        lopo_flags[held] = _fit(D[~held]).predict(D[held].values) == -1
    agreement = float((lopo_flags == flags).mean())
    recovered = int((lopo_flags & flags).sum())

    # Crew-wide shift: a timepoint where >= 3 astronauts show >= 2 strong deviations
    # at once points to sample handling / batch effects rather than individual physiology.
    z_all = D.values / ref_std
    strong_counts = (np.abs(z_all) >= config.STRONG_DEVIATION_Z).sum(axis=1)
    crew_wide = (
        pd.Series(strong_counts >= 2, index=wide.index).groupby(wide["timepoint"]).sum()
    )
    crew_wide_shift_timepoints = sorted(crew_wide[crew_wide >= 3].index.tolist())

    ceiling_by_sample = (
        long_df[long_df["at_assay_upper_limit"]].groupby("sample_name")["analyte_key"].apply(set).to_dict()
    )

    results = wide[["participant_id", "sample_name", "timepoint", "day_offset", "phase"]].copy()
    results["anomaly_score"] = scores.round(4)
    results["is_atypical"] = flags
    results["lopo_is_atypical"] = lopo_flags
    z = D.values / ref_std
    results["n_strong_deviations"] = (np.abs(z) >= config.STRONG_DEVIATION_Z).sum(axis=1)
    results["top_biomarkers"] = [
        "; ".join(
            f"{e['analyte']} z={e['z']:+.1f}"
            for e in explain(D.values[i], ref_std, wide.loc[idx, F].to_dict(), ceiling_by_sample.get(wide.loc[idx, "sample_name"], set()))[:3]
        )
        for i, idx in enumerate(wide.index)
    ]
    results.to_csv(config.ANOMALY_RESULTS_FILE, index=False)

    config.ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    artifact = {
        "model": model,
        "features": F,
        "baseline_timepoints": list(config.BASELINE_TIMEPOINTS),
        "baselines": baselines,
        "ref_std": ref_std,
        "ceiling_by_sample": ceiling_by_sample,
        "crew_wide_shift_timepoints": crew_wide_shift_timepoints,
        "trained_at": datetime.now(timezone.utc).isoformat(),
    }
    joblib.dump(artifact, config.BIOMARKER_MODEL_FILE)

    card = {
        "name": "ASTRA biomarker Isolation Forest",
        "data": {"study": config.STUDY_ID, "url": config.STUDY_URL, "source": "NASA (real)", "samples": int(len(wide)),
                 "participants": int(wide["participant_id"].nunique()), "biomarkers": F},
        "method": {
            "features": "log fold-change of percent-of-crew-pre-flight-mean vs the astronaut's personal baseline",
            "baseline_timepoints": list(config.BASELINE_TIMEPOINTS),
            "baseline_rationale": "L-44 excluded: crew-wide simultaneous drop suggests a pre-analytical/batch effect",
            "algorithm": "sklearn.ensemble.IsolationForest",
            "n_estimators": config.IF_N_ESTIMATORS,
            "contamination": config.IF_CONTAMINATION,
            "contamination_meaning": "technical choice: expected share of statistically atypical samples, not a clinical threshold",
            "random_state": config.RANDOM_STATE,
            "score": "anomaly_score = -decision_function; > 0 means statistically atypical",
        },
        "evaluation": {
            "flagged_samples": results.loc[results["is_atypical"], "sample_name"].tolist(),
            "lopo_flag_agreement": round(agreement, 3),
            "lopo_flags_recovered": f"{recovered}/{int(flags.sum())}",
            "crew_wide_shift_timepoints": crew_wide_shift_timepoints,
            "note": "28 samples / 4 participants with repeated measures: no independent test set is possible. "
                    "LOPO agreement is high mostly because most samples are typical; when an astronaut is held out, "
                    f"{recovered} of {int(flags.sum())} flags are recovered, so individual flags are NOT stable. "
                    "Treat flags as prompts to look, not as findings.",
        },
        "limitations": [
            "Exploratory proof of concept, not validated, not a diagnosis.",
            "Atypical = statistically unusual for this dataset, not medically abnormal.",
            "Point-in-time serum samples, not continuous in-flight monitoring.",
            "Some haptoglobin values sit at an apparent assay upper limit (censored).",
        ],
        "trained_at": artifact["trained_at"],
        "validation_report": report,
    }
    config.MODEL_CARD_FILE.write_text(json.dumps(card, indent=2, default=str), encoding="utf-8")
    return card


_cache: dict | None = None


def load() -> dict:
    global _cache
    if _cache is None:
        if not config.BIOMARKER_MODEL_FILE.exists():
            train()
        _cache = joblib.load(config.BIOMARKER_MODEL_FILE)
    return _cache


def reset_cache() -> None:
    global _cache
    _cache = None


def score(participant_id: str, timepoint: str, percents: dict[str, float], sample_name: str | None = None) -> dict:
    art = load()
    missing = [k for k in F if k not in percents]
    if missing:
        raise ValueError(f"Missing biomarkers: {missing}")
    dev = deviation(art["baselines"], participant_id, timepoint, percents)
    s = float(-art["model"].decision_function(dev.reshape(1, -1))[0])
    flag = bool(art["model"].predict(dev.reshape(1, -1))[0] == -1)
    name = sample_name or f"{participant_id}_serum_{timepoint}"
    contributions = explain(dev, art["ref_std"], percents, art["ceiling_by_sample"].get(name, set()))
    notes = []
    if timepoint in art.get("crew_wide_shift_timepoints", []):
        notes.append(
            f"Crew-wide shift at {timepoint}: at least 3 of 4 astronauts deviate strongly at the same "
            "timepoint. Possible sample-handling or batch effect; check before interpreting."
        )
    if any(c["at_assay_upper_limit"] for c in contributions):
        notes.append("Some values sit at an apparent assay upper limit (censored), so true values may be higher.")
    return {
        "data_quality_notes": notes,
        "model": "isolation_forest_biomarkers",
        "anomaly_score": round(s, 4),
        "threshold": 0.0,
        "is_atypical": flag,
        "n_strong_deviations": sum(1 for c in contributions if abs(c["z"]) >= config.STRONG_DEVIATION_Z),
        "strong_z": config.STRONG_DEVIATION_Z,
        "contributions": contributions,
        "baseline_timepoints": art["baseline_timepoints"],
    }


if __name__ == "__main__":
    c = train()
    print(json.dumps(c["evaluation"], indent=2))
