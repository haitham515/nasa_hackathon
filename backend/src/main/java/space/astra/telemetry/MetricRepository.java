package space.astra.telemetry;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricRepository extends JpaRepository<TelemetryMetric, String> {
    List<TelemetryMetric> findAllByOrderBySortOrderAsc();
}
