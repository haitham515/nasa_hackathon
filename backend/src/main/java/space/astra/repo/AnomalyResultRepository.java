package space.astra.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import space.astra.domain.AnomalyResult;

public interface AnomalyResultRepository extends JpaRepository<AnomalyResult, Long> {
    Optional<AnomalyResult> findByReadingId(Long readingId);

    List<AnomalyResult> findByReadingIdIn(Collection<Long> readingIds);
}
