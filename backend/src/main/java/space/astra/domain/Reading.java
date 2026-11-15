package space.astra.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** A biomarker panel (NASA) or a vital-signs reading (SIMULATED). */
@Entity
@Table(name = "reading")
public class Reading {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long runId;
    public String participantId;
    @Enumerated(EnumType.STRING)
    public Enums.Source source;
    @Enumerated(EnumType.STRING)
    public Enums.ReadingKind kind;
    public String timepoint;
    public String missionPhase;
    public double dayOffset;
    public String sampleName;
    public String study;
    public Double hrBpm;
    public Double hrvRmssdMs;
    public OffsetDateTime receivedAt;
}
