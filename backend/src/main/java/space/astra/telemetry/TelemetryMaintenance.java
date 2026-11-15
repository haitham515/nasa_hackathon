package space.astra.telemetry;

import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps a 24/7 stream from growing the database forever: drops old samples not tied to an anomaly. */
@Component
public class TelemetryMaintenance {
    private static final Logger log = LoggerFactory.getLogger(TelemetryMaintenance.class);

    private final SampleRepository samples;
    private final int retentionHours;

    public TelemetryMaintenance(SampleRepository samples, @Value("${astra.telemetry.retention-hours:24}") int retentionHours) {
        this.samples = samples;
        this.retentionHours = retentionHours;
    }

    @Scheduled(fixedDelayString = "${astra.telemetry.retention-check-ms:3600000}", initialDelay = 60_000)
    @Transactional
    public void purge() {
        int removed = samples.deleteUnlinkedOlderThan(OffsetDateTime.now().minusHours(retentionHours));
        if (removed > 0) log.info("Telemetry retention: removed {} samples older than {} h", removed, retentionHours);
    }
}
