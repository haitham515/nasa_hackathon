package space.astra.telemetry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import space.astra.domain.Enums.HealthStatus;

/** Request / response records for the telemetry API. */
public final class TelemetryDtos {
    private TelemetryDtos() {}

    public record SampleIn(@NotBlank String metric, double value, @NotNull OffsetDateTime recordedAt) {}

    public record IngestBatch(@NotEmpty List<@Valid SampleIn> samples) {}

    public record SampleDto(long id, String metric, double value, OffsetDateTime recordedAt, HealthStatus status,
                            Long eventId) {
        static SampleDto of(TelemetrySample s) {
            return new SampleDto(s.id, s.metricKey, s.value, s.recordedAt, s.status, s.eventId);
        }
    }

    public record MetricDto(String key, String label, String unit, Double normalLow, Double normalHigh,
                            Double reviewLow, Double reviewHigh, double displayMin, double displayMax,
                            int windowSeconds, String description) {
        static MetricDto of(TelemetryMetric m) {
            return new MetricDto(m.metricKey, m.label, m.unit, m.normalLow, m.normalHigh, m.reviewLow, m.reviewHigh,
                    m.displayMin, m.displayMax, m.windowSeconds, m.description);
        }
    }

    public record EventDto(long id, String metric, String direction, HealthStatus worstStatus, double breachedLimit,
                           double peakValue, OffsetDateTime peakAt, OffsetDateTime startedAt,
                           OffsetDateTime lastBreachAt, OffsetDateTime endedAt, boolean open, int sampleCount) {
        static EventDto of(AnomalyEvent e) {
            return new EventDto(e.id, e.metricKey, e.direction, e.worstStatus, e.breachedLimit, e.peakValue, e.peakAt,
                    e.startedAt, e.lastBreachAt, e.endedAt, e.open, e.sampleCount);
        }
    }

    public record AstronautDto(String id, String displayName, String role, String mission, OffsetDateTime missionStart,
                               OffsetDateTime serverTime) {}

    public record Snapshot(AstronautDto astronaut, List<MetricDto> metrics, Map<String, List<SampleDto>> windows,
                           List<EventDto> recentEvents, Map<String, Object> stream) {}

    public record TrendStats(int n, double min, double max, double mean, double slopePerMinute, int windowSeconds) {}

    public record LatestReading(String metric, String label, String unit, Double value, HealthStatus status,
                                OffsetDateTime recordedAt) {}

    public record EventDetail(EventDto event, MetricDto metric, SampleDto selected, List<SampleDto> context,
                              TrendStats trend, List<LatestReading> otherMetrics, AstronautDto astronaut) {}

    public record AnalysisDto(long eventId, HealthStatus severity, String provider, String model,
                              String fallbackReason, Map<String, Object> analysis, OffsetDateTime createdAt) {}

    public record InjectRequest(@NotBlank String metric) {}
}
