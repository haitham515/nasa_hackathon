package space.astra.telemetry;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import space.astra.telemetry.TelemetryDtos.*;

/** Read side: dashboard snapshot, event detail with surrounding trend. */
@Service
@Transactional(readOnly = true)
public class TelemetryQueryService {

    /** Seconds of context shown on each side of a selected anomaly point. */
    public static final int CONTEXT_SECONDS = 90;

    private final AstronautRepository astronauts;
    private final MetricRepository metrics;
    private final SampleRepository samples;
    private final EventRepository events;

    public TelemetryQueryService(AstronautRepository astronauts, MetricRepository metrics, SampleRepository samples,
                                 EventRepository events) {
        this.astronauts = astronauts;
        this.metrics = metrics;
        this.samples = samples;
        this.events = events;
    }

    public AstronautDto astronaut() {
        Astronaut a = astronauts.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No astronaut configured"));
        return new AstronautDto(a.id, a.displayName, a.role, a.mission, a.missionStart, OffsetDateTime.now());
    }

    public List<MetricDto> metrics() {
        return metrics.findAllByOrderBySortOrderAsc().stream().map(MetricDto::of).toList();
    }

    public Snapshot snapshot(Map<String, Object> streamStatus) {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, List<SampleDto>> windows = new LinkedHashMap<>();
        for (TelemetryMetric m : metrics.findAllByOrderBySortOrderAsc()) {
            windows.put(m.metricKey, samples
                    .findByMetricKeyAndRecordedAtAfterOrderByRecordedAtAsc(m.metricKey, now.minusSeconds(m.windowSeconds))
                    .stream().map(SampleDto::of).toList());
        }
        return new Snapshot(astronaut(), metrics(), windows, recentEvents(30), streamStatus);
    }

    public List<EventDto> recentEvents(int limit) {
        return events.findAllByOrderByIdDesc(Limit.of(Math.min(Math.max(limit, 1), 200))).stream().map(EventDto::of).toList();
    }

    public EventDetail eventDetail(long eventId, Long sampleId) {
        AnomalyEvent e = events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event " + eventId));
        TelemetryMetric m = metrics.findById(e.metricKey).orElseThrow();

        TelemetrySample selected = sampleId == null ? null : samples.findById(sampleId).orElse(null);
        if (selected != null && !Objects.equals(selected.eventId, e.id)) selected = null;
        OffsetDateTime anchor = selected != null ? selected.recordedAt : e.peakAt;

        OffsetDateTime from = minTime(anchor.minusSeconds(CONTEXT_SECONDS), e.startedAt.minusSeconds(30));
        OffsetDateTime to = anchor.plusSeconds(CONTEXT_SECONDS);
        List<TelemetrySample> ctx = samples.findByMetricKeyAndRecordedAtBetweenOrderByRecordedAtAsc(m.metricKey, from, to);

        SampleDto selectedDto = selected != null ? SampleDto.of(selected) : ctx.stream()
                .filter(s -> s.recordedAt.equals(e.peakAt)).findFirst().map(SampleDto::of).orElse(null);

        List<LatestReading> others = new ArrayList<>();
        for (TelemetryMetric other : metrics.findAllByOrderBySortOrderAsc()) {
            if (other.metricKey.equals(m.metricKey)) continue;
            Optional<TelemetrySample> latest = samples.findFirstByMetricKeyOrderByRecordedAtDesc(other.metricKey);
            others.add(new LatestReading(other.metricKey, other.label, other.unit, latest.map(s -> s.value).orElse(null),
                    latest.map(s -> s.status).orElse(null), latest.map(s -> s.recordedAt).orElse(null)));
        }

        return new EventDetail(EventDto.of(e), MetricDto.of(m), selectedDto, ctx.stream().map(SampleDto::of).toList(),
                stats(ctx, from, to), others, astronaut());
    }

    static TrendStats stats(List<TelemetrySample> ctx, OffsetDateTime from, OffsetDateTime to) {
        int window = (int) java.time.Duration.between(from, to).getSeconds();
        if (ctx.isEmpty()) return new TrendStats(0, 0, 0, 0, 0, window);
        DoubleSummaryStatistics st = ctx.stream().mapToDouble(s -> s.value).summaryStatistics();
        // Least-squares slope, value units per minute.
        double t0 = ctx.get(0).recordedAt.toEpochSecond();
        double n = ctx.size(), sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (TelemetrySample s : ctx) {
            double x = (s.recordedAt.toEpochSecond() - t0) / 60.0;
            sx += x; sy += s.value; sxx += x * x; sxy += x * s.value;
        }
        double denom = n * sxx - sx * sx;
        double slope = denom == 0 ? 0 : (n * sxy - sx * sy) / denom;
        return new TrendStats(ctx.size(), st.getMin(), st.getMax(), st.getAverage(), slope, window);
    }

    private static OffsetDateTime minTime(OffsetDateTime a, OffsetDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
