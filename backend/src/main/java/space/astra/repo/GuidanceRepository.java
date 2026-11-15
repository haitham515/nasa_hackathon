package space.astra.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import space.astra.domain.Guidance;

public interface GuidanceRepository extends JpaRepository<Guidance, Long> {
    Optional<Guidance> findByCode(String code);
}
