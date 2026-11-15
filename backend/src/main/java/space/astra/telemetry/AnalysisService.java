package space.astra.telemetry;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import space.astra.ai.AiClient;
import space.astra.service.JsonSupport;
import space.astra.telemetry.TelemetryDtos.*;

/**
 * Generates (via the AI service) and caches one clinical analysis per anomaly event.
 * Regenerates when the event has escalated since the cached analysis was written.
 */
@Service
public class AnalysisService {

    private final TelemetryQueryService query;
    private final AnalysisRepository analyses;
    private final AiClient ai;
    private final JsonSupport json;
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    public AnalysisService(TelemetryQueryService query, AnalysisRepository analyses, AiClient ai, JsonSupport json) {
        this.query = query;
        this.analyses = analyses;
        this.ai = ai;
        this.json = json;
    }

    public Optional<AnalysisDto> cached(long eventId) {
        return analyses.findByEventId(eventId).map(this::toDto);
    }

    public AnalysisDto getOrGenerate(long eventId, Long sampleId) {
        // One generation per event at a time; the lock is not held inside a DB transaction.
        synchronized (locks.computeIfAbsent(eventId, k -> new Object())) {
            // Analysis is per episode: always anchored on the event peak, whichever point was clicked.
            EventDetail detail = query.eventDetail(eventId, null);
            Optional<AiAnalysis> existing = analyses.findByEventId(eventId);
            if (existing.isPresent() && !detail.event().worstStatus().isWorseThan(existing.get().severity)
                    && !isStaleFallback(existing.get())) {
                return toDto(existing.get());
            }

            Map<String, Object> result;
            try {
                result = ai.analyze(buildRequest(detail));
            } catch (RestClientException e) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable: " + e.getMessage(), e);
            }

            AiAnalysis a = existing.orElseGet(AiAnalysis::new);
            a.eventId = eventId;
            a.severity = detail.event().worstStatus();
            a.provider = String.valueOf(result.getOrDefault("provider", "rule-based"));
            a.model = (String) result.get("model");
            a.fallbackReason = (String) result.get("fallback_reason");
            a.payloadJson = json.write(result.getOrDefault("analysis", Map.of()));
            a.createdAt = OffsetDateTime.now();
            return toDto(analyses.save(a));
        }
    }

    /** Seconds after which a fallback (AI failed) analysis is retried, e.g. once credits or a key are added. */
    static final long FALLBACK_RETRY_SECONDS = 120;

    static boolean isStaleFallback(AiAnalysis a) {
        return a.fallbackReason != null
                && a.createdAt.isBefore(OffsetDateTime.now().minusSeconds(FALLBACK_RETRY_SECONDS));
    }

    /** Structured context sent to the AI service. Only simulated telemetry; no personal data. */
    Map<String, Object> buildRequest(EventDetail d) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("metric", d.metric().key());
        event.put("label", d.metric().label());
        event.put("unit", d.metric().unit());
        event.put("severity", d.event().worstStatus().name());
        event.put("direction", d.event().direction());
        event.put("breached_limit", d.event().breachedLimit());
        event.put("peak_value", d.event().peakValue());
        event.put("started_at", d.event().startedAt().toString());
        event.put("ended_at", d.event().endedAt() == null ? null : d.event().endedAt().toString());
        event.put("still_open", d.event().open());
        event.put("normal_low", d.metric().normalLow());
        event.put("normal_high", d.metric().normalHigh());
        event.put("review_low", d.metric().reviewLow());
        event.put("review_high", d.metric().reviewHigh());

        Map<String, Object> selected = new LinkedHashMap<>();
        if (d.selected() != null) {
            selected.put("value", d.selected().value());
            selected.put("recorded_at", d.selected().recordedAt().toString());
            selected.put("status", d.selected().status().name());
        }

        List<Double> recent = downsample(d.context().stream().map(SampleDto::value).toList(), 40);
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("window_seconds", d.trend().windowSeconds());
        trend.put("n", d.trend().n());
        trend.put("min", round(d.trend().min()));
        trend.put("max", round(d.trend().max()));
        trend.put("mean", round(d.trend().mean()));
        trend.put("slope_per_minute", round(d.trend().slopePerMinute()));
        trend.put("values", recent.stream().map(AnalysisService::round).toList());

        List<Map<String, Object>> others = d.otherMetrics().stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("metric", o.metric());
            m.put("label", o.label());
            m.put("unit", o.unit());
            m.put("value", o.value() == null ? null : round(o.value()));
            m.put("status", o.status() == null ? null : o.status().name());
            return m;
        }).toList();

        Duration met = Duration.between(d.astronaut().missionStart(),
                d.selected() != null ? d.selected().recordedAt() : d.event().peakAt());
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("astronaut", Map.of("id", d.astronaut().id(), "role", d.astronaut().role(), "mission", d.astronaut().mission()));
        req.put("mission_elapsed", formatMet(met));
        req.put("event", event);
        req.put("selected_sample", selected);
        req.put("trend", trend);
        req.put("other_metrics", others);
        return req;
    }

    private AnalysisDto toDto(AiAnalysis a) {
        return new AnalysisDto(a.eventId, a.severity, a.provider, a.model, a.fallbackReason,
                json.readMap(a.payloadJson), a.createdAt);
    }

    static String formatMet(Duration d) {
        long s = Math.max(0, d.getSeconds());
        return String.format(Locale.ROOT, "%dd %02d:%02d:%02d", s / 86400, (s % 86400) / 3600, (s % 3600) / 60, s % 60);
    }

    private static List<Double> downsample(List<Double> values, int max) {
        if (values.size() <= max) return values;
        List<Double> out = new ArrayList<>();
        double step = (double) values.size() / max;
        for (int i = 0; i < max; i++) out.add(values.get((int) Math.floor(i * step)));
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
