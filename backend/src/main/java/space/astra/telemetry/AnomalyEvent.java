package space.astra.telemetry;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import space.astra.domain.Enums.HealthStatus;

/** One threshold-breach episode: opens on the first breach, closes after several normal samples. */
@Entity
@Table(name = "anomaly_event")
public class AnomalyEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String metricKey;
    public String direction;
    @Enumerated(EnumType.STRING)
    public HealthStatus worstStatus;
    public double breachedLimit;
    public double peakValue;
    public OffsetDateTime peakAt;
    public OffsetDateTime startedAt;
    public OffsetDateTime lastBreachAt;
    public OffsetDateTime endedAt;
    @Column(name = "is_open")
    public boolean open;
    public int normalStreak;
    public int sampleCount;
}
