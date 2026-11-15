package space.astra.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static space.astra.domain.Enums.HealthStatus.*;

import org.junit.jupiter.api.Test;

class ThresholdRulesTest {

    private static TelemetryMetric heartRate() {
        TelemetryMetric m = new TelemetryMetric();
        m.metricKey = "heart_rate";
        m.normalLow = 50.0;
        m.normalHigh = 100.0;
        m.reviewLow = 40.0;
        m.reviewHigh = 130.0;
        return m;
    }

    @Test
    void bandsAndLimits() {
        TelemetryMetric m = heartRate();
        assertThat(ThresholdRules.evaluate(m, 72).status()).isEqualTo(NORMAL);
        assertThat(ThresholdRules.evaluate(m, 100).status()).isEqualTo(NORMAL);   // edge is inside
        assertThat(ThresholdRules.evaluate(m, 50).status()).isEqualTo(NORMAL);
        var high = ThresholdRules.evaluate(m, 101);
        assertThat(high.status()).isEqualTo(ATTENTION);
        assertThat(high.direction()).isEqualTo("HIGH");
        assertThat(high.breachedLimit()).isEqualTo(100.0);
        var review = ThresholdRules.evaluate(m, 131);
        assertThat(review.status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(review.breachedLimit()).isEqualTo(130.0);
        assertThat(ThresholdRules.evaluate(m, 45).status()).isEqualTo(ATTENTION);
        assertThat(ThresholdRules.evaluate(m, 39).direction()).isEqualTo("LOW");
    }

    @Test
    void oneSidedLimits() {
        TelemetryMetric crp = new TelemetryMetric();
        crp.normalHigh = 3.0;
        crp.reviewHigh = 10.0;
        assertThat(ThresholdRules.evaluate(crp, 0.1).status()).isEqualTo(NORMAL);
        assertThat(ThresholdRules.evaluate(crp, 4).status()).isEqualTo(ATTENTION);
        assertThat(ThresholdRules.evaluate(crp, 11).status()).isEqualTo(REVIEW_REQUIRED);
    }

    @Test
    void extremeDependsOnDirection() {
        assertThat(ThresholdRules.isMoreExtreme("HIGH", 140, 130)).isTrue();
        assertThat(ThresholdRules.isMoreExtreme("LOW", 35, 38)).isTrue();
        assertThat(ThresholdRules.isMoreExtreme("LOW", 40, 38)).isFalse();
    }
}
