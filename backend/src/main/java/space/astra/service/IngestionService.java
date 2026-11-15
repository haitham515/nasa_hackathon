package space.astra.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import space.astra.ai.AiClient;
import space.astra.domain.*;
import space.astra.domain.Enums.*;
import space.astra.repo.*;
import space.astra.web.dto.Dtos.IngestRequest;
import space.astra.web.dto.Dtos.MeasurementIn;
import space.astra.web.dto.Dtos.ReadingEvent;

/** Ingest -> store -> score (AI service) -> notification status -> alert -> live push. */
@Service
public class IngestionService {

    private final ParticipantRepository participants;
    private final ReadingRepository readings;
    private final MeasurementRepository measurements;
    private final AnomalyResultRepository results;
    private final AlertRepository alerts;
    private final AnalysisRunRepository runs;
    private final AiClient ai;
    private final JsonSupport json;
    private final SseBroadcaster sse;

    public IngestionService(ParticipantRepository participants, ReadingRepository readings,
                            MeasurementRepository measurements, AnomalyResultRepository results,
                            AlertRepository alerts, AnalysisRunRepository runs, AiClient ai, JsonSupport json,
                            SseBroadcaster sse) {
        this.participants = participants;
        this.readings = readings;
        this.measurements = measurements;
        this.results = results;
        this.alerts = alerts;
        this.runs = runs;
        this.ai = ai;
        this.json = json;
        this.sse = sse;
    }

    @Transactional
    public ReadingEvent ingest(IngestRequest req) {
        if (!participants.existsById(req.participantId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown participant " + req.participantId());
        }
        validateShape(req);

        Reading r = new Reading();
        r.runId = req.runId();
        r.participantId = req.participantId();
        r.source = req.source();
        r.kind = req.kind();
        r.timepoint = req.timepoint();
        r.missionPhase = req.missionPhase();
        r.dayOffset = req.dayOffset();
        r.sampleName = req.sampleName();
        r.study = req.study();
        r.hrBpm = req.hrBpm();
        r.hrvRmssdMs = req.hrvRmssdMs();
        r.receivedAt = OffsetDateTime.now();
        r = readings.save(r);

        Outcome outcome = req.kind() == ReadingKind.BIOMARKER_PANEL ? handlePanel(r, req.measurements()) : handleVitals(r);

        if (r.runId != null) {
            Alert created = outcome.alert();
            runs.findById(r.runId).ifPresent(run -> {
                run.readingsCount++;
                if (created != null) run.alertsCount++;
            });
        }

        ReadingEvent event = new ReadingEvent(r.id, r.participantId, r.source, r.kind, r.timepoint, r.missionPhase,
                r.dayOffset, r.hrBpm, r.hrvRmssdMs, outcome.result().anomalyScore, outcome.result().isAtypical,
                outcome.result().status, outcome.alert() == null ? null : outcome.alert().id);
        sse.publish("reading", event);
        if (outcome.alert() != null) sse.publish("alert", Map.of("id", outcome.alert().id));
        return event;
    }

    private void validateShape(IngestRequest req) {
        if (req.kind() == ReadingKind.BIOMARKER_PANEL && (req.measurements() == null || req.measurements().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A biomarker panel needs measurements");
        }
        if (req.kind() == ReadingKind.VITALS && (req.hrBpm() == null || req.hrvRmssdMs() == null)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Vitals need hrBpm and hrvRmssdMs");
        }
    }

    private record Outcome(AnomalyResult result, Alert alert) {}

    // ---------------------------------------------------------------- biomarkers (NASA)

    private Outcome handlePanel(Reading r, List<MeasurementIn> in) {
        Map<String, Double> percents = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (MeasurementIn m : in) {
            BiomarkerMeasurement bm = new BiomarkerMeasurement();
            bm.readingId = r.id;
            bm.analyteKey = m.analyteKey();
            bm.nasaLabel = m.nasaLabel();
            bm.concentration = m.concentration();
            bm.unit = m.unit();
            bm.percentOfCrewPreflightMean = m.percent();
            bm.atAssayUpperLimit = m.atAssayUpperLimit();
            measurements.save(bm);
            percents.put(m.analyteKey(), m.percent());
            labels.put(m.analyteKey(), m.nasaLabel());
        }

        Map<String, Object> score = callAi(() -> ai.scoreBiomarkers(r.participantId, r.timepoint, r.sampleName, percents));
        boolean atypical = AiClient.bool(score, "is_atypical");
        int strong = (int) AiClient.num(score, "n_strong_deviations");
        boolean previousAtypical = previousResult(r, ReadingKind.BIOMARKER_PANEL).map(p -> p.isAtypical).orElse(false);
        HealthStatus status = StatusRules.biomarker(atypical, strong, previousAtypical);
        List<String> notes = AiClient.strings(score, "data_quality_notes");

        AnomalyResult res = saveResult(r, score, "isolation_forest_biomarkers", atypical, strong, status, notes);

        Alert alert = null;
        if (status != HealthStatus.NORMAL) {
            boolean crewWide = notes.stream().anyMatch(n -> n.startsWith("Crew-wide"));
            AlertCategory category = crewWide ? AlertCategory.DATA_QUALITY : AlertCategory.PHYSIOLOGY;
            List<Map<String, Object>> top = JsonSupport.listOfMaps(score, "contributions").stream().limit(3).toList();
            String topText = top.stream().map(c -> describe(c, labels)).collect(Collectors.joining("; "));
            String title = crewWide
                    ? "Possible data-quality issue at " + r.timepoint
                    : strong + " biomarker" + (strong == 1 ? "" : "s") + " far from personal baseline";
            String message = (atypical ? "Isolation Forest flagged this panel as statistically atypical. "
                    : "Not flagged by the model, but several biomarkers deviate strongly. ") + "Top: " + topText + ".";
            String guidance = crewWide ? "DQ_BATCH" : status == HealthStatus.REVIEW_REQUIRED ? "BIO_REVIEW" : "BIO_ATTENTION";
            alert = saveAlert(r, Stream.BIOMARKERS, status, category, title, message, res.anomalyScore, guidance);
        }
        return new Outcome(res, alert);
    }

    private static String describe(Map<String, Object> c, Map<String, String> labels) {
        String key = String.valueOf(c.get("analyte"));
        double fold = AiClient.num(c, "fold_change_vs_baseline");
        double z = AiClient.num(c, "z");
        String foldText = fold >= 1 ? String.format(java.util.Locale.ROOT, "x%.1f", fold) : String.format(java.util.Locale.ROOT, "x%.2f", fold);
        return String.format(java.util.Locale.ROOT, "%s %s vs baseline (z %+.1f)", labels.getOrDefault(key, key), foldText, z);
    }

    // ---------------------------------------------------------------- vitals (SIMULATED)

    private Outcome handleVitals(Reading r) {
        Map<String, Object> score = callAi(() -> ai.scoreVitals(r.participantId, r.hrBpm, r.hrvRmssdMs));
        boolean atypical = AiClient.bool(score, "is_atypical");

        List<Reading> previous = readings.findTop2ByParticipantIdAndKindAndIdLessThanOrderByIdDesc(
                r.participantId, ReadingKind.VITALS, r.id);
        int consecutive = 0;
        for (int i = 0; i < previous.size(); i++) {
            Optional<AnomalyResult> pr = results.findByReadingId(previous.get(i).id);
            if (pr.isPresent() && pr.get().isAtypical && consecutive == i) consecutive++;
        }
        HealthStatus status = StatusRules.vitals(atypical, consecutive);
        HealthStatus alertedInPhase = alerts
                .findFirstByParticipantIdAndStreamAndMissionPhaseOrderByIdDesc(r.participantId, Stream.VITALS, r.missionPhase)
                .map(a -> a.status).orElse(null);

        AnomalyResult res = saveResult(r, score, "isolation_forest_vitals", atypical, 0, status, List.of());

        Alert alert = null;
        if (StatusRules.shouldAlertVitals(status, alertedInPhase)) {
            double dHr = AiClient.num(score, "hr_delta_bpm");
            double dHrv = AiClient.num(score, "hrv_delta_pct");
            String title = String.format(java.util.Locale.ROOT, "[SIMULATED] Heart rate %+.0f bpm vs resting baseline", dHr);
            String message = String.format(java.util.Locale.ROOT, 
                    "SIMULATED vitals, not NASA data. HR %.0f bpm (%+.0f), HRV %.0f ms (%+.0f%%).%s",
                    r.hrBpm, dHr, r.hrvRmssdMs, dHrv,
                    status == HealthStatus.REVIEW_REQUIRED ? " Atypical for 3 readings in a row." : "");
            String guidance = switch (r.missionPhase) {
                case "IN_FLIGHT" -> "VIT_INFLIGHT";
                case "POST_FLIGHT" -> "VIT_POSTLANDING";
                default -> "VIT_GENERAL";
            };
            alert = saveAlert(r, Stream.VITALS, status, AlertCategory.PHYSIOLOGY, title, message, res.anomalyScore, guidance);
        }
        return new Outcome(res, alert);
    }

    // ---------------------------------------------------------------- helpers

    private Optional<AnomalyResult> previousResult(Reading r, ReadingKind kind) {
        return readings.findFirstByParticipantIdAndKindAndIdLessThanOrderByIdDesc(r.participantId, kind, r.id)
                .flatMap(p -> results.findByReadingId(p.id));
    }

    private AnomalyResult saveResult(Reading r, Map<String, Object> score, String model, boolean atypical, int strong,
                                     HealthStatus status, List<String> notes) {
        AnomalyResult res = new AnomalyResult();
        res.readingId = r.id;
        res.model = model;
        res.anomalyScore = AiClient.num(score, "anomaly_score");
        res.threshold = AiClient.num(score, "threshold");
        res.isAtypical = atypical;
        res.nStrongDeviations = strong;
        res.status = status;
        res.detailsJson = json.write(score);
        res.dataQualityNotes = notes.isEmpty() ? null : String.join("\n", notes);
        res.createdAt = OffsetDateTime.now();
        return results.save(res);
    }

    private Alert saveAlert(Reading r, Stream stream, HealthStatus status, AlertCategory category, String title,
                            String message, double score, String guidanceCode) {
        Alert a = new Alert();
        a.readingId = r.id;
        a.participantId = r.participantId;
        a.source = r.source;
        a.stream = stream;
        a.status = status;
        a.category = category;
        a.title = title;
        a.message = message;
        a.anomalyScore = score;
        a.timepoint = r.timepoint;
        a.missionPhase = r.missionPhase;
        a.dayOffset = r.dayOffset;
        a.guidanceCode = guidanceCode;
        a.createdAt = OffsetDateTime.now();
        return alerts.save(a);
    }

    private static Map<String, Object> callAi(java.util.function.Supplier<Map<String, Object>> call) {
        try {
            return call.get();
        } catch (org.springframework.web.client.RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable: " + e.getMessage(), e);
        }
    }
}
