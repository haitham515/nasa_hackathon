package space.astra.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import space.astra.domain.Participant;

public interface ParticipantRepository extends JpaRepository<Participant, String> {}
