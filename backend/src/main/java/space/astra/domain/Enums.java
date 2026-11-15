package space.astra.domain;

/** Shared enumerations. Kept together because each is tiny. */
public final class Enums {
    private Enums() {}

    /** Where a reading comes from. SIMULATED data must never be shown as NASA data. */
    public enum Source { NASA, SIMULATED }

    public enum ReadingKind { BIOMARKER_PANEL, VITALS }

    /** Monitoring stream a status/alert belongs to. */
    public enum Stream { BIOMARKERS, VITALS }

    /** Software notification levels. Not a diagnosis. Order matters (severity). */
    public enum HealthStatus { NORMAL, ATTENTION, REVIEW_REQUIRED;
        public boolean isWorseThan(HealthStatus other) { return other == null || ordinal() > other.ordinal(); }
    }

    public enum AlertCategory { PHYSIOLOGY, DATA_QUALITY }

    public enum RunStatus { RUNNING, FINISHED, STOPPED, FAILED }
}
