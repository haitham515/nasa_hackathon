package space.astra.telemetry;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AstronautRepository extends JpaRepository<Astronaut, String> {
    Optional<Astronaut> findFirstByOrderByIdAsc();
}
