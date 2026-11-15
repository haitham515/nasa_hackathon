package space.astra.web;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import space.astra.ai.AiClient;
import space.astra.domain.AnalysisRun;
import space.astra.service.*;
import space.astra.web.dto.Dtos.*;

/** REST API consumed by the React dashboard and by the replay engine. */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final IngestionService ingestion;
    private final CrewService crew;
    private final AlertService alerts;
    private final ReplayService replay;
    private final ExportService export;
    private final SseBroadcaster sse;
    private final AiClient ai;

    public ApiController(IngestionService ingestion, CrewService crew, AlertService alerts, ReplayService replay,
                         ExportService export, SseBroadcaster sse, AiClient ai) {
        this.ingestion = ingestion;
        this.crew = crew;
        this.alerts = alerts;
        this.replay = replay;
        this.export = export;
        this.sse = sse;
        this.ai = ai;
    }

    // ---- ingestion -------------------------------------------------------------------

    @PostMapping("/readings")
    public ReadingEvent ingest(@Valid @RequestBody IngestRequest req) {
        return ingestion.ingest(req);
    }

    // ---- crew ------------------------------------------------------------------------

    @GetMapping("/crew")
    public List<CrewStatus> crew() {
        return crew.crew();
    }

    @GetMapping("/crew/{participantId}/timeline")
    public Timeline timeline(@PathVariable String participantId) {
        return crew.timeline(participantId);
    }

    // ---- alerts ----------------------------------------------------------------------

    @GetMapping("/alerts")
    public List<AlertSummary> alerts(@RequestParam(required = false) String participantId,
                                     @RequestParam(defaultValue = "50") int limit) {
        return alerts.list(participantId, limit);
    }

    @GetMapping("/alerts/{id}")
    public AlertDetail alert(@PathVariable long id) {
        return alerts.detail(id);
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public AlertSummary acknowledge(@PathVariable long id) {
        return alerts.acknowledge(id);
    }

    @GetMapping("/guidance")
    public List<GuidanceDto> guidance() {
        return alerts.guidance();
    }

    // ---- replay ----------------------------------------------------------------------

    @PostMapping("/replay/start")
    public AnalysisRun start(@RequestBody(required = false) ReplayStartRequest req) {
        double speed = req == null || req.speed() == null ? 1.0 : Math.max(0.1, Math.min(20, req.speed()));
        boolean simulate = req == null || req.simulate() == null || req.simulate();
        return replay.start(speed, simulate);
    }

    @PostMapping("/replay/stop")
    public Map<String, Object> stop() {
        replay.stop();
        return replay.status();
    }

    @PostMapping("/replay/reset")
    public Map<String, Object> reset() {
        replay.reset();
        return replay.status();
    }

    /** Callback from the replay engine when a run completes. */
    @PostMapping("/replay/finished")
    public void finished(@RequestBody ReplayFinished body) {
        replay.finished(body.runId());
    }

    @GetMapping("/replay/status")
    public Map<String, Object> status() {
        return replay.status();
    }

    @GetMapping("/runs")
    public List<AnalysisRun> runs() {
        return replay.history();
    }

    // ---- exports ---------------------------------------------------------------------

    @GetMapping(value = "/export/nasa-biomarker-results.csv", produces = "text/csv")
    public ResponseEntity<String> exportNasa() {
        return csv("astra_nasa_osd575_biomarker_results.csv", export.nasaBiomarkerResults());
    }

    @GetMapping(value = "/export/simulated-vitals.csv", produces = "text/csv")
    public ResponseEntity<String> exportSimulated() {
        return csv("astra_SIMULATED_vitals.csv", export.simulatedVitals());
    }

    private static ResponseEntity<String> csv(String filename, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(body);
    }

    // ---- live stream + metadata --------------------------------------------------------

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sse.subscribe();
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dataset", Map.of(
                "name", "SpaceX Inspiration4 - serum cardiovascular panel (Eve Technologies HDCVD9)",
                "study", "OSD-575",
                "url", "https://osdr.nasa.gov/bio/repo/data/studies/OSD-575",
                "participants", 4, "timepoints", List.of("L-92", "L-44", "L-3", "R+1", "R+45", "R+82", "R+194"),
                "samples", 28,
                "normalisation", "Measurements normalized by average of pre-flight values from all crew (OSD-575 metadata)"));
        out.put("simulation", Map.of(
                "what", "Heart rate and HRV readings",
                "why", "No downloadable in-flight wearable data for this crew was found; the stream demonstrates the live pipeline",
                "anchoredTo", "Direction of effects reported in Nature 2024 (Inspiration4 Apple Watch data); magnitudes are invented",
                "paper", "https://www.nature.com/articles/s41586-024-07648-x"));
        out.put("disclaimer", AlertService.DISCLAIMER);
        try {
            out.put("modelCard", ai.modelCard());
        } catch (RestClientException e) {
            out.put("modelCard", Map.of("error", "AI service unavailable"));
        }
        return out;
    }
}
