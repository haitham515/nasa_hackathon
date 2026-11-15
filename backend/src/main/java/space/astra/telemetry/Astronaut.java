package space.astra.telemetry;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** The single monitored astronaut (simulated profile). */
@Entity
@Table(name = "astronaut")
public class Astronaut {
    @Id
    public String id;
    public String displayName;
    public String role;
    public String mission;
    public OffsetDateTime missionStart;
}
