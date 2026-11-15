package space.astra.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "participant")
public class Participant {
    @Id
    public String id;
    public String sex;
    public String displayName;
}
