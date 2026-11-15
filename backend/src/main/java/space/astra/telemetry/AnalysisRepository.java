package space.astra.telemetry;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<AiAnalysis, Long> {
    Optional<AiAnalysis> findByEventId(Long eventId);
}
