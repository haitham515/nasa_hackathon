package space.astra.domain;

import jakarta.persistence.*;

/** Fixed, NASA-sourced guidance text shown next to alerts. */
@Entity
@Table(name = "guidance")
public class Guidance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String code;
    public String title;
    public String body;
    public String sourceName;
    public String sourceUrl;
}
