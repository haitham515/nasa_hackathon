package space.astra.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Software notification. Never a diagnosis or an automated medical recommendation. */
@Entity
@Table(name = "alert")
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long readingId;
    public String participantId;
    @Enumerated(EnumType.STRING)
    public Enums.Source source;
    @Enumerated(EnumType.STRING)
    public Enums.Stream stream;
    @Enumerated(EnumType.STRING)
    public Enums.HealthStatus status;
    @Enumerated(EnumType.STRING)
    public Enums.AlertCategory category;
    public String title;
    public String message;
    public double anomalyScore;
    public String timepoint;
    public String missionPhase;
    public double dayOffset;
    public String guidanceCode;
    public OffsetDateTime createdAt;
    public boolean acknowledged;
    public OffsetDateTime acknowledgedAt;
}
