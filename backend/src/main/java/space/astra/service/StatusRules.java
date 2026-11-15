package space.astra.service;

import space.astra.domain.Enums.HealthStatus;

/**
 * Notification rules. These turn model outputs into software notification levels;
 * they are NOT clinical thresholds.
 *
 * Biomarkers (NASA, one panel per sampling timepoint):
 *   REVIEW_REQUIRED  model flags the panel AND (>= 2 strong deviations OR previous panel also flagged)
 *   ATTENTION        model flags the panel OR >= 2 strong per-biomarker deviations
 *   NORMAL           otherwise
 *
 * Vitals (SIMULATED stream):
 *   REVIEW_REQUIRED  3 consecutive atypical readings
 *   ATTENTION        current reading atypical
 *   NORMAL           otherwise
 */
public final class StatusRules {
    public static final int STRONG_DEVIATIONS_FOR_ESCALATION = 2;
    public static final int CONSECUTIVE_VITALS_FOR_REVIEW = 3;

    private StatusRules() {}

    public static HealthStatus biomarker(boolean atypical, int strongDeviations, boolean previousPanelAtypical) {
        boolean manyStrong = strongDeviations >= STRONG_DEVIATIONS_FOR_ESCALATION;
        if (atypical && (manyStrong || previousPanelAtypical)) return HealthStatus.REVIEW_REQUIRED;
        if (atypical || manyStrong) return HealthStatus.ATTENTION;
        return HealthStatus.NORMAL;
    }

    /** @param previousConsecutiveAtypical number of immediately preceding atypical readings */
    public static HealthStatus vitals(boolean atypical, int previousConsecutiveAtypical) {
        if (!atypical) return HealthStatus.NORMAL;
        if (previousConsecutiveAtypical + 1 >= CONSECUTIVE_VITALS_FOR_REVIEW) return HealthStatus.REVIEW_REQUIRED;
        return HealthStatus.ATTENTION;
    }

    /**
     * Vitals arrive often, so alert at most once per level per mission phase: only when the
     * status is worse than anything already alerted for this astronaut in the current phase.
     *
     * @param alreadyAlertedInPhase highest status already alerted in this phase, or null if none
     */
    public static boolean shouldAlertVitals(HealthStatus current, HealthStatus alreadyAlertedInPhase) {
        return current != HealthStatus.NORMAL && current.isWorseThan(alreadyAlertedInPhase);
    }
}
