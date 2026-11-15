package space.astra.telemetry;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SampleRepository extends JpaRepository<TelemetrySample, Long> {
    List<TelemetrySample> findByMetricKeyAndRecordedAtAfterOrderByRecordedAtAsc(String metricKey, OffsetDateTime after);

    List<TelemetrySample> findByMetricKeyAndRecordedAtBetweenOrderByRecordedAtAsc(
            String metricKey, OffsetDateTime from, OffsetDateTime to);

    Optional<TelemetrySample> findFirstByMetricKeyOrderByRecordedAtDesc(String metricKey);

    @Modifying
    @Query("delete from TelemetrySample s where s.recordedAt < :cutoff and s.eventId is null")
    int deleteUnlinkedOlderThan(@Param("cutoff") OffsetDateTime cutoff);
}
