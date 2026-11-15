package space.astra.telemetry;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import space.astra.domain.Enums.HealthStatus;
import space.astra.service.SseBroadcaster;
import space.astra.telemetry.TelemetryDtos.*;

/** Ingest -> threshold status -> anomaly episode grouping -> live push. */
@Service
public class TelemetryService {

    /** Consecutive normal samples needed to close an anomaly episode (avoids flapping). */
    public static final int NORMAL_SAMPLES_TO_CLOSE = 3;

    private final MetricRepository metrics;
    private final SampleRepository samples;
    private final EventRepository events;
    private final SseBroadcaster sse;

    public TelemetryService(MetricRepository metrics, SampleRepository samples, EventRepository events,
                            SseBroadcaster sse) {
        this.metrics = metrics;
        this.samples = samples;
        this.events = events;
        this.sse = sse;
    }

    /** Batches arrive from a single stream thread; synchronized keeps episode grouping consistent anyway. */
    @Transactional
    public synchronized List<SampleDto> ingest(IngestBatch batch) {
        Map<String, TelemetryMetric> byKey = new HashMap<>();
        metrics.findAll().forEach(m -> byKey.put(m.metricKey, m));

        List<SampleIn> ordered = new ArrayList<>(batch.samples());
        ordered.sort(Comparator.comparing(SampleIn::recordedAt));

        List<SampleDto> out = new ArrayList<>();
        List<Map<String, Object>> anomalyEvents = new ArrayList<>();
        for (SampleIn in : ordered) {
            TelemetryMetric m = byKey.get(in.metric());
            if (m == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown metric " + in.metric());
            ThresholdRules.Evaluation ev = ThresholdRules.evaluate(m, in.value());

            TelemetrySample s = new TelemetrySample();
            s.metricKey = m.metricKey;
            s.value = in.value();
            s.recordedAt = in.recordedAt();
            s.status = ev.status();

            Optional<AnomalyEvent> openEvent = events.findFirstByMetricKeyAndOpenTrueOrderByIdDesc(m.metricKey);
            if (ev.status() != HealthStatus.NORMAL) {
                AnomalyEvent e = openEvent.orElse(null);
                String change;
                if (e == null) {
                    e = new AnomalyEvent();
                    e.metricKey = m.metricKey;
                    e.direction = ev.direction();
                    e.worstStatus = ev.status();
                    e.breachedLimit = ev.breachedLimit();
                    e.peakValue = in.value();
                    e.peakAt = in.recordedAt();
                    e.startedAt = in.recordedAt();
                    e.open = true;
                    change = "opened";
                } else {
                    change = null;
                    if (ev.status().isWorseThan(e.worstStatus)) {
                        e.worstStatus = ev.status();
                        e.breachedLimit = ev.breachedLimit();
                        change = "escalated";
                    }
                    if (ThresholdRules.isMoreExtreme(e.direction, in.value(), e.peakValue)) {
                        e.peakValue = in.value();
                        e.peakAt = in.recordedAt();
                    }
                }
                e.lastBreachAt = in.recordedAt();
                e.normalStreak = 0;
                e.sampleCount++;
                e = events.save(e);
                s.eventId = e.id;
                if (change != null) anomalyEvents.add(Map.of("type", change, "event", EventDto.of(e)));
            } else if (openEvent.isPresent()) {
                AnomalyEvent e = openEvent.get();
                e.normalStreak++;
                if (e.normalStreak >= NORMAL_SAMPLES_TO_CLOSE) {
                    e.open = false;
                    e.endedAt = in.recordedAt();
                    anomalyEvents.add(Map.of("type", "closed", "event", EventDto.of(e)));
                }
            }
            out.add(SampleDto.of(samples.save(s)));
        }

        sse.publish("telemetry", Map.of("samples", out));
        anomalyEvents.forEach(a -> sse.publish("anomaly", a));
        return out;
    }

    /** Close every open episode, e.g. when the stream is paused so nothing stays "open" forever. */
    @Transactional
    public synchronized void closeOpenEvents(OffsetDateTime at) {
        events.findAll().stream().filter(e -> e.open).forEach(e -> {
            e.open = false;
            e.endedAt = at;
            sse.publish("anomaly", Map.of("type", "closed", "event", EventDto.of(e)));
        });
    }
}
