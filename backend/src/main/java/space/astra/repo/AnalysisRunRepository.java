package space.astra.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import space.astra.domain.AnalysisRun;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {
    List<AnalysisRun> findTop20ByOrderByIdDesc();
}
