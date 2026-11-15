package space.astra.service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.astra.domain.*;
import space.astra.domain.Enums.ReadingKind;
import space.astra.domain.Enums.Source;
import space.astra.repo.*;

/** CSV exports. Real NASA results and SIMULATED vitals are exported separately, never mixed. */
@Service
@Transactional(readOnly = true)
public class ExportService {

    private final ReadingRepository readings;
    private final MeasurementRepository measurements;
    private final AnomalyResultRepository results;

    public ExportService(ReadingRepository readings, MeasurementRepository measurements, AnomalyResultRepository results) {
        this.readings = readings;
        this.measurements = measurements;
        this.results = results;
    }

    public String nasaBiomarkerResults() {
        List<Reading> panels = readings.findBySourceAndKindOrderByParticipantIdAscIdAsc(Source.NASA, ReadingKind.BIOMARKER_PANEL);
        List<Long> ids = panels.stream().map(r -> r.id).toList();
        Map<Long, AnomalyResult> res = byReading(results.findByReadingIdIn(ids));
        Map<Long, List<BiomarkerMeasurement>> meas = measurements.findByReadingIdIn(ids).stream()
                .collect(Collectors.groupingBy(m -> m.readingId));

        StringBuilder sb = new StringBuilder();
        sb.append("# Source: NASA OSDR OSD-575 (Inspiration4), Eve Technologies HDCVD9 cardiovascular panel\n");
        sb.append("# Exploratory analysis. 'atypical' = statistically unusual, not medically abnormal.\n");
        sb.append("participant_id,sample_name,timepoint,day_offset,mission_phase,analyte_key,nasa_label,concentration,unit,"
                + "percent_of_crew_preflight_mean,at_assay_upper_limit,anomaly_score,is_atypical,n_strong_deviations,status\n");
        for (Reading r : panels) {
            AnomalyResult a = res.get(r.id);
            for (BiomarkerMeasurement m : meas.getOrDefault(r.id, List.of())) {
                sb.append(String.join(",", r.participantId, csv(r.sampleName), r.timepoint, num(r.dayOffset), r.missionPhase,
                        m.analyteKey, csv(m.nasaLabel), num(m.concentration), m.unit, num(m.percentOfCrewPreflightMean),
                        String.valueOf(m.atAssayUpperLimit), a == null ? "" : num(a.anomalyScore),
                        a == null ? "" : String.valueOf(a.isAtypical), a == null ? "" : String.valueOf(a.nStrongDeviations),
                        a == null ? "" : a.status.name())).append('\n');
            }
        }
        return sb.toString();
    }

    public String simulatedVitals() {
        List<Reading> vit = readings.findBySourceAndKindOrderByParticipantIdAscIdAsc(Source.SIMULATED, ReadingKind.VITALS);
        Map<Long, AnomalyResult> res = byReading(results.findByReadingIdIn(vit.stream().map(r -> r.id).toList()));
        StringBuilder sb = new StringBuilder();
        sb.append("# SIMULATED DATA - NOT NASA MEASUREMENTS. Generated to demonstrate the live pipeline.\n");
        sb.append("source,participant_id,segment,mission_phase,day_offset,hr_bpm,hrv_rmssd_ms,anomaly_score,is_atypical,status\n");
        for (Reading r : vit) {
            AnomalyResult a = res.get(r.id);
            sb.append(String.join(",", "SIMULATED", r.participantId, r.timepoint, r.missionPhase, num(r.dayOffset),
                    num(r.hrBpm), num(r.hrvRmssdMs), a == null ? "" : num(a.anomalyScore),
                    a == null ? "" : String.valueOf(a.isAtypical), a == null ? "" : a.status.name())).append('\n');
        }
        return sb.toString();
    }

    private static Map<Long, AnomalyResult> byReading(List<AnomalyResult> list) {
        return list.stream().collect(Collectors.toMap(a -> a.readingId, Function.identity()));
    }

    private static String num(Double v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String num(double v) {
        return String.valueOf(v);
    }

    private static String csv(String s) {
        if (s == null) return "";
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
