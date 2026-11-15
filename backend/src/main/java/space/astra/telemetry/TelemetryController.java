package space.astra.telemetry;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import space.astra.ai.AiClient;
import space.astra.service.SseBroadcaster;
import space.astra.telemetry.TelemetryDtos.*;

/** API for the single-astronaut 24/7 Flight Surgeon dashboard. */
@RestController
@RequestMapping("/api")
public class TelemetryController {

    private static final List<String> INJECTABLE = List.of("heart_rate", "heart_rate_low", "crp", "fibrinogen", "troponin_i");

    private final TelemetryService telemetry;
    private final TelemetryQueryService query;
    private final AnalysisService analysis;
    private final AiClient ai;
    private final SseBroadcaster sse;

    public TelemetryController(TelemetryService telemetry, TelemetryQueryService query, AnalysisService analysis,
                               AiClient ai, SseBroadcaster sse) {
        this.telemetry = telemetry;
        this.query = query;
        this.analysis = analysis;
        this.ai = ai;
        this.sse = sse;
    }

    @GetMapping("/astronaut")
    public AstronautDto astronaut() {
        return query.astronaut();
    }

    @GetMapping("/telemetry/metrics")
    public List<MetricDto> metrics() {
        return query.metrics();
    }

    /** Everything the dashboard needs on load: profile, metrics, recent windows, recent events, stream state. */
    @GetMapping("/telemetry/snapshot")
    public Snapshot snapshot() {
        return query.snapshot(streamStatusSafe());
    }

    @GetMapping("/telemetry/events")
    public List<EventDto> events(@RequestParam(defaultValue = "30") int limit) {
        return query.recentEvents(limit);
    }

    @GetMapping("/telemetry/events/{id}")
    public EventDetail event(@PathVariable long id, @RequestParam(required = false) Long sampleId) {
        return query.eventDetail(id, sampleId);
    }

    @GetMapping("/telemetry/events/{id}/analysis")
    public ResponseEntity<AnalysisDto> cachedAnalysis(@PathVariable long id) {
        return analysis.cached(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/telemetry/events/{id}/analysis")
    public AnalysisDto analyze(@PathVariable long id, @RequestParam(required = false) Long sampleId) {
        return analysis.getOrGenerate(id, sampleId);
    }

    /** Called by the stream generator (or a real sensor gateway). */
    @PostMapping("/telemetry/ingest")
    public Map<String, Object> ingest(@Valid @RequestBody IngestBatch batch) {
        return Map.of("accepted", telemetry.ingest(batch).size());
    }

    // ---- stream control (proxied to the AI service, where the generator runs) ----

    @GetMapping("/telemetry/stream")
    public Map<String, Object> streamStatus() {
        return streamStatusSafe();
    }

    @PostMapping("/telemetry/stream/pause")
    public Map<String, Object> pause() {
        Map<String, Object> s = call(ai::pauseStream);
        telemetry.closeOpenEvents(OffsetDateTime.now());
        sse.publish("stream", s);
        return s;
    }

    @PostMapping("/telemetry/stream/resume")
    public Map<String, Object> resume() {
        Map<String, Object> s = call(ai::resumeStream);
        sse.publish("stream", s);
        return s;
    }

    @PostMapping("/telemetry/stream/inject")
    public Map<String, Object> inject(@Valid @RequestBody InjectRequest req) {
        if (!INJECTABLE.contains(req.metric())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Cannot inject " + req.metric());
        }
        return call(() -> ai.injectAnomaly(req.metric()));
    }

    private Map<String, Object> streamStatusSafe() {
        try {
            return ai.streamStatus();
        } catch (RestClientException e) {
            Map<String, Object> down = new LinkedHashMap<>();
            down.put("running", false);
            down.put("paused", false);
            down.put("error", "AI service unreachable");
            return down;
        }
    }

    private static Map<String, Object> call(java.util.function.Supplier<Map<String, Object>> fn) {
        try {
            return fn.get();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable: " + e.getMessage(), e);
        }
    }
}
