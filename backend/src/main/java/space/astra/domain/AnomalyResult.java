package space.astra.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "anomaly_result")
public class AnomalyResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long readingId;
    public String model;
    public double anomalyScore;
    public double threshold;
    public boolean isAtypical;
    public int nStrongDeviations;
    @Enumerated(EnumType.STRING)
    public Enums.HealthStatus status;
    /** Raw JSON returned by the AI service (contributions, deltas...). */
    public String detailsJson;
    /** Newline-separated data-quality notes. */
    public String dataQualityNotes;
    public OffsetDateTime createdAt;
}
