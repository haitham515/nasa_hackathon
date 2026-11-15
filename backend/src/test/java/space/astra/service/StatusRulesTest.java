package space.astra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static space.astra.domain.Enums.HealthStatus.*;

import org.junit.jupiter.api.Test;

class StatusRulesTest {

    @Test
    void biomarkerRules() {
        assertThat(StatusRules.biomarker(false, 0, false)).isEqualTo(NORMAL);
        assertThat(StatusRules.biomarker(false, 1, true)).isEqualTo(NORMAL);
        assertThat(StatusRules.biomarker(false, 2, false)).isEqualTo(ATTENTION);
        assertThat(StatusRules.biomarker(true, 0, false)).isEqualTo(ATTENTION);
        assertThat(StatusRules.biomarker(true, 7, false)).isEqualTo(REVIEW_REQUIRED);
        assertThat(StatusRules.biomarker(true, 0, true)).isEqualTo(REVIEW_REQUIRED);
    }

    @Test
    void vitalsNeedThreeConsecutiveForReview() {
        assertThat(StatusRules.vitals(false, 5)).isEqualTo(NORMAL);
        assertThat(StatusRules.vitals(true, 0)).isEqualTo(ATTENTION);
        assertThat(StatusRules.vitals(true, 1)).isEqualTo(ATTENTION);
        assertThat(StatusRules.vitals(true, 2)).isEqualTo(REVIEW_REQUIRED);
    }

    @Test
    void vitalsAlertOncePerLevelPerPhase() {
        assertThat(StatusRules.shouldAlertVitals(NORMAL, null)).isFalse();
        assertThat(StatusRules.shouldAlertVitals(ATTENTION, null)).isTrue();
        assertThat(StatusRules.shouldAlertVitals(ATTENTION, ATTENTION)).isFalse();
        assertThat(StatusRules.shouldAlertVitals(ATTENTION, REVIEW_REQUIRED)).isFalse();
        assertThat(StatusRules.shouldAlertVitals(REVIEW_REQUIRED, ATTENTION)).isTrue();
    }
}
