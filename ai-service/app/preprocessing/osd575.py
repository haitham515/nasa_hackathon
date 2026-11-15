"""Load, validate and tidy the NASA OSD-575 cardiovascular panel (Eve HDCVD9).

The raw file stays untouched in data/raw/. This module produces:
  * data/processed/cardiovascular_data.csv       (long, one row per analyte x sample)
  * data/processed/cardiovascular_wide_percent.csv (one row per sample, model input)
  * data/processed/validation_report.json        (what was checked and found)

Notes from the OSD-575 ISA metadata:
  "Measurements were normalized by average of pre-flight values from all crew."
  -> `percent` = concentration / crew pre-flight mean * 100. It is derived from
     `concentration`, so only ONE of the two is used as model input (percent).
"""
from __future__ import annotations

import json
from pathlib import Path

import pandas as pd

from app import config

RAW_COLUMNS = ["Analyte", "Concentration", "Timepoint", "ID", "Unit", "Timepoint2", "Type", "Percent"]


def load_raw(path: Path = config.RAW_CARDIO_FILE) -> pd.DataFrame:
    # utf-8-sig strips the BOM present in the NASA file.
    df = pd.read_csv(path, encoding="utf-8-sig")
    missing = [c for c in RAW_COLUMNS if c not in df.columns]
    if missing:
        raise ValueError(f"Unexpected NASA file layout, missing columns: {missing}")
    return df


def validate(df: pd.DataFrame) -> dict:
    """Return a JSON-serialisable report. Raises on blocking problems."""
    report: dict = {
        "source_file": config.RAW_CARDIO_FILE.name,
        "study": config.STUDY_ID,
        "rows": int(len(df)),
        "columns": list(df.columns),
        "missing_values": {c: int(v) for c, v in df.isna().sum().items()},
        "duplicate_rows": int(df.duplicated().sum()),
        "duplicate_keys": int(df.duplicated(subset=["Analyte", "Timepoint", "ID"]).sum()),
        "participants": sorted(df["ID"].unique().tolist()),
        "timepoints": sorted(df["Timepoint"].unique().tolist(), key=lambda t: config.TIMEPOINTS.get(t, 9999)),
        "analytes": sorted(df["Analyte"].unique().tolist()),
        "units_by_analyte": df.groupby("Analyte")["Unit"].unique().apply(list).to_dict(),
        "assay_types": df["Type"].unique().tolist(),
        "non_numeric_concentration": int(pd.to_numeric(df["Concentration"], errors="coerce").isna().sum()),
        "non_numeric_percent": int(pd.to_numeric(df["Percent"], errors="coerce").isna().sum()),
    }
    unknown_tp = set(report["timepoints"]) - set(config.TIMEPOINTS)
    unknown_an = set(report["analytes"]) - set(config.ANALYTES)
    report["unknown_timepoints"] = sorted(unknown_tp)
    report["unknown_analytes"] = sorted(unknown_an)
    problems = []
    if unknown_tp:
        problems.append(f"unknown timepoints {sorted(unknown_tp)}")
    if unknown_an:
        problems.append(f"unknown analytes {sorted(unknown_an)}")
    if report["duplicate_keys"]:
        problems.append("duplicate analyte/timepoint/participant rows")
    if report["non_numeric_concentration"] or report["non_numeric_percent"]:
        problems.append("non-numeric measurements")
    report["blocking_problems"] = problems
    if problems:
        raise ValueError("NASA file failed validation: " + "; ".join(problems))
    return report


def tidy(df: pd.DataFrame) -> pd.DataFrame:
    """Harmonise names while keeping the original NASA labels alongside."""
    out = pd.DataFrame(
        {
            "participant_id": df["ID"],
            "sample_name": df["ID"] + "_serum_" + df["Timepoint"],
            "timepoint": df["Timepoint"],
            "day_offset": df["Timepoint"].map(config.TIMEPOINTS).astype(int),
            "phase": df["Timepoint"].map(lambda t: "PRE_FLIGHT" if t in config.PREFLIGHT_TIMEPOINTS else "POST_FLIGHT"),
            "analyte_key": df["Analyte"].map(config.ANALYTES),
            "analyte_nasa_label": df["Analyte"],
            "concentration": pd.to_numeric(df["Concentration"]),
            "unit": df["Unit"],
            "percent_of_crew_preflight_mean": pd.to_numeric(df["Percent"]),
            "nasa_timepoint_group": df["Timepoint2"],
            "assay_panel": df["Type"],
            "source": "NASA",
            "study": config.STUDY_ID,
        }
    )
    out["at_assay_upper_limit"] = _assay_ceiling_mask(out)
    return out.sort_values(["participant_id", "day_offset", "analyte_key"]).reset_index(drop=True)


def _assay_ceiling_mask(long_df: pd.DataFrame) -> pd.Series:
    """Flag values that look like an assay upper limit: the analyte's maximum
    concentration repeated identically in >= 3 samples (e.g. haptoglobin 307.64 %
    appears 7 times). Those values are censored, not true measurements."""
    mask = pd.Series(False, index=long_df.index)
    for _, grp in long_df.groupby("analyte_key"):
        counts = grp["concentration"].value_counts()
        repeated = counts[counts >= 3].index
        for value in repeated:
            # Only repeated values in the upper quartile are treated as a ceiling.
            if value >= grp["concentration"].quantile(0.75):
                mask.loc[grp.index[grp["concentration"] == value]] = True
    return mask


def to_wide(long_df: pd.DataFrame) -> pd.DataFrame:
    """One row per sample, one column per analyte (percent representation)."""
    wide = long_df.pivot_table(
        index=["participant_id", "sample_name", "timepoint", "day_offset", "phase"],
        columns="analyte_key",
        values="percent_of_crew_preflight_mean",
    ).reset_index()
    wide.columns.name = None
    return wide[["participant_id", "sample_name", "timepoint", "day_offset", "phase", *config.FEATURES]].sort_values(
        ["participant_id", "day_offset"]
    ).reset_index(drop=True)


def run() -> tuple[pd.DataFrame, pd.DataFrame, dict]:
    raw = load_raw()
    report = validate(raw)
    long_df = tidy(raw)
    wide = to_wide(long_df)
    report["processed_samples"] = int(len(wide))
    report["transformations"] = [
        "Read raw CSV with utf-8-sig (removes BOM); raw file left unmodified.",
        "Validated columns, missing values, duplicates, numeric types, known timepoints/analytes.",
        "Renamed columns to snake_case; kept original NASA analyte label and timepoint group.",
        "Added day_offset relative to launch (Inspiration4: 3-day mission, R+N = day 3+N).",
        "Added phase PRE_FLIGHT (L-92, L-44, L-3) / POST_FLIGHT (R+1..R+194).",
        "Flagged values repeated identically >= 3 times in the upper quartile as probable assay upper limits.",
        "Pivoted percent-of-crew-pre-flight-mean to one row per sample for modelling.",
    ]
    ceiling = long_df[long_df["at_assay_upper_limit"]]
    report["assay_upper_limit_values"] = (
        ceiling.groupby("analyte_key")["sample_name"].apply(list).to_dict() if len(ceiling) else {}
    )
    config.PROCESSED_DIR.mkdir(parents=True, exist_ok=True)
    long_df.to_csv(config.PROCESSED_LONG_FILE, index=False)
    wide.to_csv(config.PROCESSED_WIDE_FILE, index=False)
    config.VALIDATION_REPORT_FILE.write_text(json.dumps(report, indent=2, default=str), encoding="utf-8")
    return long_df, wide, report


if __name__ == "__main__":
    _, w, r = run()
    print(f"OK: {r['rows']} raw rows -> {len(w)} samples, participants={r['participants']}")
