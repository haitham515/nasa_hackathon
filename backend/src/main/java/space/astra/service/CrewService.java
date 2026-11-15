package space.astra.service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import space.astra.ai.AiClient;
import space.astra.domain.*;
import space.astra.domain.Enums.ReadingKind;
import space.astra.repo.*;
import space.astra.web.dto.Dtos.*;

/** Read side: crew status board and per-astronaut timelines. */
@Service
@Transactional(readOnly = true)
public class CrewService {

    private final ParticipantRepository participants;
    private final ReadingRepository readings;
    private final MeasurementRepository measurements;
    private final AnomalyResultRepository results;
    private final JsonSupport json;

    public CrewService(ParticipantRepository participants, ReadingRepository readings,
                       MeasurementRepository measurements, AnomalyResultRepository results, JsonSupport json) {
        this.participants = participants;
        this.readings = readings;
        this.measurements = measurements;
        this.results = results;
        this.json = json;
    }

    public List<CrewStatus> crew() {
        return participants.findAll(org.springframework.data.domain.Sort.by("id")).stream().map(this::status).toList();
    }

    private CrewStatus status(Participant p) {
        Optional<Reading> panel = readings.findFirstByParticipantIdAndKindOrderByIdDesc(p.id, ReadingKind.BIOMARKER_PANEL);
        Optional<Reading> vit = readings.findFirstByParticipantIdAndKindOrderByIdDesc(p.id, ReadingKind.VITALS);
        Optional<AnomalyResult> panelRes = panel.flatMap(r -> results.findByReadingId(r.id));
        Optional<AnomalyResult> vitRes = vit.flatMap(r -> results.findByReadingId(r.id));

        String summary = panelRes.map(this::summarise).orElse("Waiting for first NASA sample");
        Double hrDelta = vitRes.map(v -> AiClient.num(json.readMap(v.detailsJson), "hr_delta_bpm")).orElse(null);
        Double lastDay = maxDay(panel, vit);

        return new CrewStatus(p.id, p.sex,
                panelRes.map(r -> r.status).orElse(null), panel.map(r -> r.timepoint).orElse(null),
                panelRes.map(r -> r.anomalyScore).orElse(null), summary,
                vitRes.map(r -> r.status).orElse(null), vit.map(r -> r.hrBpm).orElse(null),
                vit.map(r -> r.hrvRmssdMs).orElse(null), hrDelta, vit.map(r -> r.missionPhase).orElse(null), lastDay);
    }

    private static Double maxDay(Optional<Reading> a, Optional<Reading> b) {
        return a.isEmpty() && b.isEmpty() ? null
                : Math.max(a.map(r -> r.dayOffset).orElse(-1e9), b.map(r -> r.dayOffset).orElse(-1e9));
    }

    private String summarise(AnomalyResult r) {
        if (r.status == Enums.HealthStatus.NORMAL) return "Biomarkers within this astronaut's usual range";
        List<Map<String, Object>> top = JsonSupport.listOfMaps(json.readMap(r.detailsJson), "contributions");
        if (top.isEmpty()) return "Atypical panel";
        Map<String, Object> c = top.get(0);
        return String.format(java.util.Locale.ROOT, "%s %s baseline (z %+.1f)", c.get("analyte"), c.get("direction"), AiClient.num(c, "z"));
    }

    public Timeline timeline(String participantId) {
        if (!participants.existsById(participantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown participant " + participantId);
        }
        List<Reading> panels = readings.findByParticipantIdAndKindOrderByIdAsc(participantId, ReadingKind.BIOMARKER_PANEL);
        List<Reading> vitals = readings.findByParticipantIdAndKindOrderByIdAsc(participantId, ReadingKind.VITALS);

        List<Long> ids = new ArrayList<>();
        panels.forEach(r -> ids.add(r.id));
        vitals.forEach(r -> ids.add(r.id));
        Map<Long, AnomalyResult> res = results.findByReadingIdIn(ids).stream()
                .collect(Collectors.toMap(a -> a.readingId, Function.identity()));
        Map<Long, List<BiomarkerMeasurement>> meas = measurements.findByReadingIdIn(panels.stream().map(r -> r.id).toList())
                .stream().collect(Collectors.groupingBy(m -> m.readingId));

        List<PanelPoint> panelPoints = panels.stream().filter(r -> res.containsKey(r.id)).map(r -> {
            AnomalyResult a = res.get(r.id);
            Map<String, Object> details = json.readMap(a.detailsJson);
            Map<String, Map<String, Object>> markers = new LinkedHashMap<>();
            Map<String, Map<String, Object>> contribByKey = JsonSupport.listOfMaps(details, "contributions").stream()
                    .collect(Collectors.toMap(c -> String.valueOf(c.get("analyte")), Function.identity(), (x, y) -> x));
            for (BiomarkerMeasurement m : meas.getOrDefault(r.id, List.of())) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("label", m.nasaLabel);
                v.put("concentration", m.concentration);
                v.put("unit", m.unit);
                v.put("percent", m.percentOfCrewPreflightMean);
                v.put("atAssayUpperLimit", m.atAssayUpperLimit);
                Map<String, Object> c = contribByKey.get(m.analyteKey);
                if (c != null) {
                    v.put("z", c.get("z"));
                    v.put("foldChange", c.get("fold_change_vs_baseline"));
                }
                markers.put(m.analyteKey, v);
            }
            List<String> notes = a.dataQualityNotes == null ? List.of() : List.of(a.dataQualityNotes.split("\n"));
            return new PanelPoint(r.id, r.timepoint, r.dayOffset, r.missionPhase, a.anomalyScore, a.isAtypical,
                    a.nStrongDeviations, a.status, markers,
                    JsonSupport.listOfMaps(details, "contributions").stream().limit(3).toList(), notes);
        }).toList();

        List<VitalsPoint> vitalPoints = vitals.stream().filter(r -> res.containsKey(r.id)).map(r -> {
            AnomalyResult a = res.get(r.id);
            return new VitalsPoint(r.id, r.timepoint, r.dayOffset, r.missionPhase, r.hrBpm, r.hrvRmssdMs,
                    a.anomalyScore, a.isAtypical, a.status);
        }).toList();

        return new Timeline(participantId, panelPoints, vitalPoints);
    }
}
