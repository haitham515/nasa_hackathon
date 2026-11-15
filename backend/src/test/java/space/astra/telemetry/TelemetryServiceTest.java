package space.astra.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import space.astra.ai.AiClient;
import space.astra.domain.Enums.HealthStatus;
import space.astra.telemetry.TelemetryDtos.IngestBatch;
import space.astra.telemetry.TelemetryDtos.SampleIn;

@SpringBootTest
@ActiveProfiles("local")
class TelemetryServiceTest {

    @Autowired TelemetryService telemetry;
    @Autowired AnalysisService analysis;
    @Autowired EventRepository events;
    @Autowired SampleRepository samples;
    @Autowired AnalysisRepository analyses;
    @MockitoBean AiClient ai;

    private final OffsetDateTime t0 = OffsetDateTime.now().minusMinutes(5).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

    @BeforeEach
    void clean() {
        analyses.deleteAll();
        samples.deleteAll();
        events.deleteAll();
    }

    private void hr(int second, double value) {
        telemetry.ingest(new IngestBatch(List.of(new SampleIn("heart_rate", value, t0.plusSeconds(second)))));
    }

    @Test
    void episodeOpensEscalatesAndClosesAfterThreeNormalSamples() {
        hr(0, 72);
        hr(1, 105);   // attention -> opens
        hr(2, 135);   // review -> escalates
        hr(3, 120);   // still attention, same event
        hr(4, 80);
        hr(5, 81);
        assertThat(events.findAll()).singleElement().satisfies(e -> {
            assertThat(e.open).isTrue();
            assertThat(e.worstStatus).isEqualTo(HealthStatus.REVIEW_REQUIRED);
            assertThat(e.peakValue).isEqualTo(135);
            assertThat(e.breachedLimit).isEqualTo(130);
            assertThat(e.sampleCount).isEqualTo(3);
        });
        hr(6, 79);    // third normal sample closes it
        AnomalyEvent closed = events.findAll().get(0);
        assertThat(closed.open).isFalse();
        assertThat(closed.endedAt).isEqualTo(t0.plusSeconds(6));
        hr(7, 104);   // a new breach opens a new episode
        assertThat(events.count()).isEqualTo(2);
    }

    @Test
    void analysisIsCachedUntilTheEventEscalates() {
        when(ai.analyze(anyMap())).thenReturn(Map.of(
                "provider", "rule-based", "analysis", Map.of("summary", "test")));
        hr(0, 110);
        long eventId = events.findAll().get(0).id;
        analysis.getOrGenerate(eventId, null);
        analysis.getOrGenerate(eventId, null);
        verify(ai, times(1)).analyze(anyMap());

        hr(1, 140);   // escalation -> stale analysis is regenerated
        analysis.getOrGenerate(eventId, null);
        verify(ai, times(2)).analyze(anyMap());
        assertThat(analysis.cached(eventId).orElseThrow().severity()).isEqualTo(HealthStatus.REVIEW_REQUIRED);
    }

    @Test
    void fallbackAnalysisIsRetriedAfterAWhile() {
        when(ai.analyze(anyMap())).thenReturn(Map.of(
                "provider", "rule-based", "fallback_reason", "OpenAI account has no credits left",
                "analysis", Map.of("summary", "fallback")));
        hr(0, 110);
        long eventId = events.findAll().get(0).id;
        analysis.getOrGenerate(eventId, null);
        analysis.getOrGenerate(eventId, null);          // recent fallback: served from cache
        verify(ai, times(1)).analyze(anyMap());

        AiAnalysis cached = analyses.findByEventId(eventId).orElseThrow();
        cached.createdAt = OffsetDateTime.now().minusSeconds(AnalysisService.FALLBACK_RETRY_SECONDS + 5);
        analyses.save(cached);
        analysis.getOrGenerate(eventId, null);          // old fallback: AI is asked again
        verify(ai, times(2)).analyze(anyMap());
    }
}
