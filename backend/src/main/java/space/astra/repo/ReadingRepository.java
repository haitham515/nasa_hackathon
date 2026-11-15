package space.astra.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import space.astra.domain.Enums;
import space.astra.domain.Reading;

public interface ReadingRepository extends JpaRepository<Reading, Long> {
    List<Reading> findByParticipantIdAndKindOrderByIdAsc(String participantId, Enums.ReadingKind kind);

    List<Reading> findBySourceAndKindOrderByParticipantIdAscIdAsc(Enums.Source source, Enums.ReadingKind kind);

    Optional<Reading> findFirstByParticipantIdAndKindAndIdLessThanOrderByIdDesc(
            String participantId, Enums.ReadingKind kind, Long id);

    List<Reading> findTop2ByParticipantIdAndKindAndIdLessThanOrderByIdDesc(
            String participantId, Enums.ReadingKind kind, Long id);

    Optional<Reading> findFirstByParticipantIdAndKindOrderByIdDesc(String participantId, Enums.ReadingKind kind);

    Optional<Reading> findFirstByOrderByIdDesc();
}
