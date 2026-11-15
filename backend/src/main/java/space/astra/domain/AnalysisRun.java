package space.astra.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** One replay/analysis session (history of executed analyses). */
@Entity
@Table(name = "analysis_run")
public class AnalysisRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public OffsetDateTime startedAt;
    public OffsetDateTime finishedAt;
    public double speed;
    public boolean simulate;
    @Enumerated(EnumType.STRING)
    public Enums.RunStatus status;
    public int readingsCount;
    public int alertsCount;
}
