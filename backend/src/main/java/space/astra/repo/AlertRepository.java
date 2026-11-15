package space.astra.repo;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import space.astra.domain.Alert;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findAllByOrderByIdDesc(Limit limit);

    List<Alert> findByParticipantIdOrderByIdDesc(String participantId, Limit limit);

    long countByAcknowledgedFalse();

    java.util.Optional<Alert> findFirstByParticipantIdAndStreamAndMissionPhaseOrderByIdDesc(
            String participantId, space.astra.domain.Enums.Stream stream, String missionPhase);
}
