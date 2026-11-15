package space.astra.telemetry;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Metric definition and thresholds. Null limits mean "no limit on that side". */
@Entity
@Table(name = "telemetry_metric")
public class TelemetryMetric {
    @Id
    public String metricKey;
    public String label;
    public String unit;
    public Double normalLow;
    public Double normalHigh;
    public Double reviewLow;
    public Double reviewHigh;
    public double displayMin;
    public double displayMax;
    public int windowSeconds;
    public int sortOrder;
    public String description;
}
