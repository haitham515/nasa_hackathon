package space.astra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import space.astra.repo.GuidanceRepository;
import space.astra.repo.ParticipantRepository;

/** Boots the app on H2, applies the Flyway migrations and validates the JPA mapping. */
@SpringBootTest
@ActiveProfiles("local")
class AstraBackendApplicationTests {

    @Autowired ParticipantRepository participants;
    @Autowired GuidanceRepository guidance;

    @Test
    void migrationsSeedCrewAndGuidance() {
        assertThat(participants.count()).isEqualTo(4);
        assertThat(guidance.findByCode("VIT_POSTLANDING")).isPresent();
    }
}
