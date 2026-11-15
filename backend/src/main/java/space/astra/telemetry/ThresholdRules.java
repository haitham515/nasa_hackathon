package space.astra.telemetry;

import space.astra.domain.Enums.HealthStatus;

/**
 * Turns a value into a notification level using the metric's limits.
 * Outside the normal band -> ATTENTION; beyond a review limit -> REVIEW_REQUIRED.
 * These are illustrative reference ranges, not clinical decision thresholds.
 */
public final class ThresholdRules {
    private ThresholdRules() {}

    /** @param breachedLimit the limit that was crossed (the most severe one), null when NORMAL */
    public record Evaluation(HealthStatus status, String direction, Double breachedLimit) {
        static final Evaluation NORMAL = new Evaluation(HealthStatus.NORMAL, null, null);
    }

    public static Evaluation evaluate(TelemetryMetric m, double value) {
        if (m.reviewHigh != null && value > m.reviewHigh) return new Evaluation(HealthStatus.REVIEW_REQUIRED, "HIGH", m.reviewHigh);
        if (m.reviewLow != null && value < m.reviewLow) return new Evaluation(HealthStatus.REVIEW_REQUIRED, "LOW", m.reviewLow);
        if (m.normalHigh != null && value > m.normalHigh) return new Evaluation(HealthStatus.ATTENTION, "HIGH", m.normalHigh);
        if (m.normalLow != null && value < m.normalLow) return new Evaluation(HealthStatus.ATTENTION, "LOW", m.normalLow);
        return Evaluation.NORMAL;
    }

    /** True when {@code candidate} is further from normal than {@code current} in the event's direction. */
    public static boolean isMoreExtreme(String direction, double candidate, double current) {
        return "LOW".equals(direction) ? candidate < current : candidate > current;
    }
}
