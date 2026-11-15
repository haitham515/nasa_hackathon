package space.astra.telemetry;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import space.astra.domain.Enums.HealthStatus;

/** Cached AI (or rule-based) analysis of one anomaly event. */
@Entity
@Table(name = "ai_analysis")
public class AiAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long eventId;
    @Enumerated(EnumType.STRING)
    public HealthStatus severity;
    public String provider;
    public String model;
    public String fallbackReason;
    public String payloadJson;
    public OffsetDateTime createdAt;
}
