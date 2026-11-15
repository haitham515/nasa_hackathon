package space.astra.telemetry;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import space.astra.domain.Enums.HealthStatus;

@Entity
@Table(name = "telemetry_sample")
public class TelemetrySample {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String metricKey;
    @Column(name = "metric_value")
    public double value;
    public OffsetDateTime recordedAt;
    @Enumerated(EnumType.STRING)
    public HealthStatus status;
    public Long eventId;
    public String source = "SIMULATED";
}
