"""Deterministic clinical knowledge base: used as the fallback when no AI provider is
available, and as grounding text in the AI prompt. Paraphrased from NASA public pages;
general decision-support content only (no drugs, no doses)."""
from __future__ import annotations

DISCLAIMER = ("Decision support generated from SIMULATED telemetry by an exploratory prototype. "
              "Not a diagnosis. The flight surgeon decides; follow mission medical procedures.")

SOURCES = {
    "cv_risk": ("NASA - Risk of Cardiovascular Adaptations", "https://www.nasa.gov/reference/risk-of-cardiovascular-adaptations/"),
    "cv_micro": ("NASA - Cardiovascular Health in Microgravity",
                 "https://www.nasa.gov/missions/station/cardiovascular-health-in-microgravity/"),
    "clots": ("NASA - How do dangerous blood clots form in space?",
              "https://science.nasa.gov/science-research/science-enabling-technology/how-do-dangerous-blood-clots-form-in-space/"),
}

# (metric, direction) -> content. Each cause: (cause, mechanism, likelihood).
KB: dict[tuple[str, str], dict] = {
    ("heart_rate", "HIGH"): {
        "interpretation": "Heart rate is above the resting reference range.",
        "causes": [
            ("Recent exercise session or exertion", "Daily resistive and aerobic countermeasure sessions raise heart rate for minutes afterward.", "high"),
            ("Reduced circulating blood volume", "Fluid shift toward the head lowers plasma volume, so the heart beats faster to keep output up.", "moderate"),
            ("Stress, sleep loss or circadian disruption", "Confinement, workload and 16 sunrises a day disturb sleep and raise sympathetic tone.", "moderate"),
            ("Dehydration or fever", "Low fluid intake or infection raises heart rate; immune changes in flight can increase infection risk.", "low"),
            ("Arrhythmia", "NASA lists heart rhythm disturbances among spaceflight cardiovascular risks.", "low"),
        ],
        "immediate": [
            ("Stop strenuous activity and rest, secured and seated, for 5 minutes", "Separates exertion from a resting abnormality."),
            ("Re-measure heart rate and check the sensor contact", "Rules out motion or electrode artifacts."),
            ("Drink fluids per the hydration schedule", "Corrects low plasma volume, a common contributor."),
            ("Note symptoms: palpitations, dizziness, chest discomfort, breathlessness", "Symptoms change the urgency."),
        ],
        "review_extra": ("Record a 12-lead ECG if available and contact the flight surgeon now", "A sustained high rate needs rhythm assessment."),
        "countermeasures": [
            ("Keep the scheduled exercise programme, adjusted by the flight surgeon", "Exercise is the primary cardiovascular countermeasure."),
            ("Follow fluid loading and compression-garment protocols before high-g phases", "Limits orthostatic intolerance on return."),
            ("Protect the sleep period", "Lower stress and better sleep reduce resting heart rate."),
        ],
        "escalation": ["Chest pain or fainting", "Heart rate above 150 bpm at rest", "Irregular rhythm on ECG", "Shortness of breath at rest"],
        "sources": ["cv_risk", "cv_micro"],
    },
    ("heart_rate", "LOW"): {
        "interpretation": "Heart rate is below the resting reference range.",
        "causes": [
            ("High vagal tone during rest or sleep", "Bradycardia during sleep is common in fit crew members.", "high"),
            ("Sensor artifact or poor electrode contact", "Missed beats make the measured rate drop.", "moderate"),
            ("Conduction disturbance", "Cardiac remodelling in microgravity can affect rhythm; rare.", "low"),
        ],
        "immediate": [
            ("Check sensor placement and re-measure", "Rules out an artifact first."),
            ("Ask about dizziness, near-fainting or unusual fatigue", "Symptoms make low heart rate significant."),
            ("If asleep or resting with no symptoms, continue monitoring", "Asymptomatic resting bradycardia is often benign."),
        ],
        "review_extra": ("Record an ECG and contact the flight surgeon", "Very low rates need rhythm assessment."),
        "countermeasures": [
            ("Keep regular aerobic exercise", "Maintains cardiovascular conditioning."),
            ("Rise slowly from rest", "Avoids symptoms from a low rate plus low blood volume."),
        ],
        "escalation": ["Fainting or near-fainting", "Heart rate below 35 bpm", "Pauses or irregular rhythm on ECG"],
        "sources": ["cv_risk", "cv_micro"],
    },
    ("crp", "HIGH"): {
        "interpretation": "C-reactive protein is above the reference range, indicating systemic inflammation.",
        "causes": [
            ("Infection", "Immune function changes in flight; viral reactivation has been observed in crew members.", "moderate"),
            ("Muscle strain from exercise", "Heavy resistive exercise can cause a mild inflammatory response.", "moderate"),
            ("Radiation and oxidative stress", "Space radiation exposure promotes inflammation.", "low"),
            ("Psychological stress and poor sleep", "Chronic stress raises inflammatory markers.", "low"),
        ],
        "immediate": [
            ("Check body temperature and note symptoms (fever, sore throat, cough, pain)", "Points toward or away from infection."),
            ("Review recent exercise, injuries and sleep", "Explains benign rises."),
            ("Hydrate and rest", "Supports recovery whatever the cause."),
        ],
        "review_extra": ("Share the trend with the flight surgeon for an infection assessment per protocol", "Values above 10 mg/L need a clinical decision."),
        "countermeasures": [
            ("Repeat the measurement at the next scheduled sampling", "Confirms whether the rise persists."),
            ("Follow crew hygiene and isolation protocols if infection is suspected", "Protects the rest of the crew."),
        ],
        "escalation": ["Fever above 38.5 °C", "CRP still rising on repeat", "Chest pain, breathlessness or severe pain"],
        "sources": ["cv_risk"],
    },
    ("fibrinogen", "HIGH"): {
        "interpretation": "Fibrinogen is above the reference range, which can increase clotting tendency.",
        "causes": [
            ("Acute-phase response to inflammation or infection", "Fibrinogen rises alongside CRP in inflammation.", "moderate"),
            ("Venous stasis in the head and neck", "Fluid shift slows blood flow in the jugular veins; NASA has found clots there in crew members.", "moderate"),
            ("Dehydration", "Concentrates plasma proteins.", "low"),
        ],
        "immediate": [
            ("Drink fluids per the hydration schedule", "Reduces haemoconcentration."),
            ("Report neck swelling, headache, facial fullness or a one-sided arm or leg ache", "Possible signs of a venous clot."),
            ("Avoid long static postures; move regularly", "Improves venous return."),
        ],
        "review_extra": ("Contact the flight surgeon; a jugular vein ultrasound may be requested per protocol", "Crew have performed guided self-ultrasound in flight."),
        "countermeasures": [
            ("Repeat the measurement and compare with CRP", "Separates inflammation from an isolated rise."),
            ("Keep exercise and hydration routines", "Supports normal blood flow."),
        ],
        "escalation": ["Neck or arm swelling or pain", "Sudden breathlessness or chest pain", "Severe headache or vision change"],
        "sources": ["clots", "cv_risk"],
    },
    ("fibrinogen", "LOW"): {
        "interpretation": "Fibrinogen is below the reference range.",
        "causes": [
            ("Measurement or sampling error", "Biosensor drift or a poor sample can read low.", "moderate"),
            ("Increased clotting factor consumption", "Rare; would need clinical assessment.", "low"),
        ],
        "immediate": [
            ("Repeat the measurement", "Confirms a true low value."),
            ("Report unusual bruising or bleeding gums", "Signs of reduced clotting ability."),
        ],
        "review_extra": ("Contact the flight surgeon", "A confirmed very low value affects bleeding risk."),
        "countermeasures": [("Avoid activities with injury risk until reviewed", "Reduces bleeding risk.")],
        "escalation": ["Any active bleeding", "Large unexplained bruises"],
        "sources": ["cv_risk"],
    },
    ("troponin_i", "HIGH"): {
        "interpretation": "High-sensitivity troponin I is above the 99th-percentile reference limit, suggesting cardiac muscle strain or injury.",
        "causes": [
            ("Very strenuous or prolonged exercise", "Intense sessions can release small amounts of troponin.", "moderate"),
            ("Cardiac stress from fluid shifts and remodelling", "The heart becomes rounder and can lose mass in microgravity.", "low"),
            ("Myocardial ischaemia or inflammation", "Uncommon, but the reason this marker needs clinical review.", "low"),
            ("Dehydration or measurement artifact", "Can raise readings without heart injury.", "low"),
        ],
        "immediate": [
            ("Stop exercise and rest", "Reduces cardiac workload while the cause is assessed."),
            ("Note any chest pain, pressure, breathlessness or palpitations", "Symptoms make this urgent."),
            ("Record an ECG if available", "Looks for signs of ischaemia or arrhythmia."),
        ],
        "review_extra": ("Contact the flight surgeon immediately and repeat troponin per protocol", "A rising troponin needs a clinical decision."),
        "countermeasures": [
            ("Pause high-intensity exercise until cleared", "Avoids further strain."),
            ("Repeat the measurement to establish the trend", "A rise-and-fall pattern guides interpretation."),
        ],
        "escalation": ["Chest pain or pressure", "Troponin still rising on repeat", "ECG changes", "Fainting or breathlessness"],
        "sources": ["cv_risk", "cv_micro"],
    },
}


def lookup(metric: str, direction: str) -> dict:
    return KB.get((metric, direction)) or KB.get((metric, "HIGH")) or next(iter(KB.values()))
