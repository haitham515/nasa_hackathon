package space.astra.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import space.astra.domain.Enums.AlertCategory;
import space.astra.domain.Enums.HealthStatus;
import space.astra.domain.Enums.ReadingKind;
import space.astra.domain.Enums.Source;
import space.astra.domain.Enums.Stream;

/** API records (request and response bodies). */
public final class Dtos {
    private Dtos() {}

    // ---- ingestion (sent by the replay engine, or by a real device integration) ----

    public record MeasurementIn(@NotBlank String analyteKey, @NotBlank String nasaLabel, double concentration,
                                @NotBlank String unit, double percent, boolean atAssayUpperLimit) {}

    public record IngestRequest(Long runId, @NotNull Source source, @NotNull ReadingKind kind,
                                @NotBlank String participantId, @NotBlank String timepoint,
                                @NotBlank String missionPhase, double dayOffset, String sampleName, String study,
                                Double hrBpm, Double hrvRmssdMs, List<@Valid MeasurementIn> measurements) {}

    // ---- live events and responses ----

    public record ReadingEvent(long readingId, String participantId, Source source, ReadingKind kind,
                               String timepoint, String missionPhase, double dayOffset, Double hrBpm,
                               Double hrvRmssdMs, double anomalyScore, boolean atypical, HealthStatus status,
                               Long alertId) {}

    public record CrewStatus(String participantId, String sex,
                             HealthStatus biomarkerStatus, String biomarkerTimepoint, Double biomarkerScore,
                             String biomarkerSummary,
                             HealthStatus vitalsStatus, Double hrBpm, Double hrvRmssdMs, Double hrDeltaBpm,
                             String vitalsPhase, Double lastDayOffset) {}

    public record PanelPoint(long readingId, String timepoint, double dayOffset, String missionPhase,
                             double anomalyScore, boolean atypical, int strongDeviations, HealthStatus status,
                             Map<String, Map<String, Object>> biomarkers, List<Map<String, Object>> topContributions,
                             List<String> dataQualityNotes) {}

    public record VitalsPoint(long readingId, String timepoint, double dayOffset, String missionPhase,
                              double hrBpm, double hrvRmssdMs, double anomalyScore, boolean atypical,
                              HealthStatus status) {}

    public record Timeline(String participantId, List<PanelPoint> panels, List<VitalsPoint> vitals) {}

    public record AlertSummary(long id, String participantId, Source source, Stream stream, HealthStatus status,
                               AlertCategory category, String title, String message, double anomalyScore,
                               String timepoint, String missionPhase, double dayOffset, OffsetDateTime createdAt,
                               boolean acknowledged) {}

    public record GuidanceDto(String code, String title, String body, String sourceName, String sourceUrl) {}

    public record AlertDetail(AlertSummary alert, Map<String, Object> details, List<Map<String, Object>> biomarkers,
                              List<String> dataQualityNotes, GuidanceDto guidance, String disclaimer) {}

    public record ReplayStartRequest(Double speed, Boolean simulate) {}

    public record ReplayFinished(Long runId, Integer sent, Integer errors) {}
}
