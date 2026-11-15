package space.astra.telemetry;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<AnomalyEvent, Long> {
    Optional<AnomalyEvent> findFirstByMetricKeyAndOpenTrueOrderByIdDesc(String metricKey);

    List<AnomalyEvent> findAllByOrderByIdDesc(Limit limit);
}
