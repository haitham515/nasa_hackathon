package space.astra.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "biomarker_measurement")
public class BiomarkerMeasurement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long readingId;
    public String analyteKey;
    public String nasaLabel;
    public double concentration;
    public String unit;
    public double percentOfCrewPreflightMean;
    public boolean atAssayUpperLimit;
}
