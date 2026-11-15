package space.astra.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import space.astra.domain.Alert;
import space.astra.domain.AnomalyResult;
import space.astra.repo.*;
import space.astra.web.dto.Dtos.*;

@Service
public class AlertService {

    public static final String DISCLAIMER = "Software notification from an exploratory prototype. "
            + "It is not a diagnosis and not an automated medical recommendation.";

    private final AlertRepository alerts;
    private final AnomalyResultRepository results;
    private final MeasurementRepository measurements;
    private final GuidanceRepository guidance;
    private final JsonSupport json;
    private final SseBroadcaster sse;

    public AlertService(AlertRepository alerts, AnomalyResultRepository results, MeasurementRepository measurements,
                        GuidanceRepository guidance, JsonSupport json, SseBroadcaster sse) {
        this.alerts = alerts;
        this.results = results;
        this.measurements = measurements;
        this.guidance = guidance;
        this.json = json;
        this.sse = sse;
    }

    @Transactional(readOnly = true)
    public List<AlertSummary> list(String participantId, int limit) {
        Limit l = Limit.of(Math.min(Math.max(limit, 1), 200));
        List<Alert> rows = participantId == null ? alerts.findAllByOrderByIdDesc(l)
                : alerts.findByParticipantIdOrderByIdDesc(participantId, l);
        return rows.stream().map(AlertService::summary).toList();
    }

    @Transactional(readOnly = true)
    public AlertDetail detail(long id) {
        Alert a = find(id);
        AnomalyResult res = results.findByReadingId(a.readingId).orElse(null);
        Map<String, Object> details = res == null ? Map.of() : json.readMap(res.detailsJson);
        List<String> notes = res == null || res.dataQualityNotes == null ? List.of() : List.of(res.dataQualityNotes.split("\n"));
        List<Map<String, Object>> biomarkers = measurements.findByReadingId(a.readingId).stream()
                .map(m -> Map.<String, Object>of("key", m.analyteKey, "label", m.nasaLabel, "concentration", m.concentration,
                        "unit", m.unit, "percent", m.percentOfCrewPreflightMean, "atAssayUpperLimit", m.atAssayUpperLimit))
                .toList();
        GuidanceDto g = a.guidanceCode == null ? null : guidance.findByCode(a.guidanceCode)
                .map(x -> new GuidanceDto(x.code, x.title, x.body, x.sourceName, x.sourceUrl)).orElse(null);
        return new AlertDetail(summary(a), details, biomarkers, notes, g, DISCLAIMER);
    }

    @Transactional
    public AlertSummary acknowledge(long id) {
        Alert a = find(id);
        if (!a.acknowledged) {
            a.acknowledged = true;
            a.acknowledgedAt = OffsetDateTime.now();
            sse.publish("alert", Map.of("id", a.id, "acknowledged", true));
        }
        return summary(a);
    }

    @Transactional(readOnly = true)
    public List<GuidanceDto> guidance() {
        return guidance.findAll().stream()
                .map(x -> new GuidanceDto(x.code, x.title, x.body, x.sourceName, x.sourceUrl)).toList();
    }

    private Alert find(long id) {
        return alerts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert " + id));
    }

    static AlertSummary summary(Alert a) {
        return new AlertSummary(a.id, a.participantId, a.source, a.stream, a.status, a.category, a.title, a.message,
                a.anomalyScore, a.timepoint, a.missionPhase, a.dayOffset, a.createdAt, a.acknowledged);
    }
}
