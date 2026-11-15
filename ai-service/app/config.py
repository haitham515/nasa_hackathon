"""Central configuration for the ASTRA AI service.

Every path can be overridden with an environment variable so the same code
runs locally and inside Docker.
"""
from __future__ import annotations

import os
from pathlib import Path

SERVICE_ROOT = Path(__file__).resolve().parent.parent
REPO_ROOT = SERVICE_ROOT.parent

DATA_DIR = Path(os.getenv("ASTRA_DATA_DIR", REPO_ROOT / "data"))
RAW_DIR = DATA_DIR / "raw"
PROCESSED_DIR = DATA_DIR / "processed"
METADATA_DIR = DATA_DIR / "metadata"
ARTIFACT_DIR = Path(os.getenv("ASTRA_ARTIFACT_DIR", SERVICE_ROOT / "artifacts"))

# Original NASA file (long format, one row per analyte x sample). Never modified.
RAW_CARDIO_FILE = RAW_DIR / "LSDS-8_Multiplex_serum.cardiovascular.EvePanel_SUBMITTED.csv"

PROCESSED_LONG_FILE = PROCESSED_DIR / "cardiovascular_data.csv"
PROCESSED_WIDE_FILE = PROCESSED_DIR / "cardiovascular_wide_percent.csv"
ANOMALY_RESULTS_FILE = PROCESSED_DIR / "anomaly_results.csv"
VALIDATION_REPORT_FILE = PROCESSED_DIR / "validation_report.json"

BIOMARKER_MODEL_FILE = ARTIFACT_DIR / "isolation_forest_biomarkers.joblib"
VITALS_MODEL_FILE = ARTIFACT_DIR / "isolation_forest_vitals_SIMULATED.joblib"
MODEL_CARD_FILE = ARTIFACT_DIR / "model_card.json"

BACKEND_URL = os.getenv("ASTRA_BACKEND_URL", "http://localhost:8080")

STUDY_ID = "OSD-575"
STUDY_URL = "https://osdr.nasa.gov/bio/repo/data/studies/OSD-575"

# Inspiration4: launch on day 0, splashdown on day 3 (3-day mission).
MISSION_DAYS = 3

# Sampling timepoints -> day offset relative to launch.
TIMEPOINTS: dict[str, int] = {
    "L-92": -92,
    "L-44": -44,
    "L-3": -3,
    "R+1": MISSION_DAYS + 1,
    "R+45": MISSION_DAYS + 45,
    "R+82": MISSION_DAYS + 82,
    "R+194": MISSION_DAYS + 194,
}
PREFLIGHT_TIMEPOINTS = ("L-92", "L-44", "L-3")

# Personal baseline used by the biomarker model. L-44 is excluded because all
# four crew members show a simultaneous drop at L-44 (e.g. CRP 7-12 %, haptoglobin
# 1-3 % of the crew mean), which looks like a pre-analytical / batch effect rather
# than individual physiology. L-44 is still scored and displayed. Override with
# ASTRA_BASELINE_TIMEPOINTS="L-92,L-44,L-3" to include it.
BASELINE_TIMEPOINTS: tuple[str, ...] = tuple(
    os.getenv("ASTRA_BASELINE_TIMEPOINTS", "L-92,L-3").split(",")
)

# Isolation Forest parameters (technical choices, NOT clinical thresholds).
IF_N_ESTIMATORS = 500
IF_CONTAMINATION = 0.10  # ~3 of 28 samples flagged as statistically atypical
STRONG_DEVIATION_Z = 3.0  # per-biomarker |z| above which a deviation is "strong"

# NASA analyte label -> stable key used in code, DB and API.
ANALYTES: dict[str, str] = {
    "a-2 Macroglobulin": "a2_macroglobulin",
    "AGP": "agp",
    "CRP": "crp",
    "Fetuin A36": "fetuin_a36",
    "Fibrinogen": "fibrinogen",
    "Haptoglobin": "haptoglobin",
    "L-Selectin": "l_selectin",
    "PF4": "pf4",
    "SAP": "sap",
}
FEATURES: list[str] = list(ANALYTES.values())

RANDOM_STATE = 42
