package space.astra.repo;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import space.astra.domain.BiomarkerMeasurement;

public interface MeasurementRepository extends JpaRepository<BiomarkerMeasurement, Long> {
    List<BiomarkerMeasurement> findByReadingIdIn(Collection<Long> readingIds);

    List<BiomarkerMeasurement> findByReadingId(Long readingId);
}
