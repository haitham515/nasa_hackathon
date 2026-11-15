package space.astra.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import space.astra.ai.AiClient;
import space.astra.domain.AnalysisRun;
import space.astra.domain.Enums.RunStatus;
import space.astra.repo.*;

/** Controls the replay engine (which lives in the AI service) and keeps run history. */
@Service
public class ReplayService {

    private final AiClient ai;
    private final AnalysisRunRepository runs;
    private final ReadingRepository readings;
    private final AlertRepository alerts;
    private final SseBroadcaster sse;
    private final org.springframework.transaction.PlatformTransactionManager txManager;

    public ReplayService(AiClient ai, AnalysisRunRepository runs, ReadingRepository readings, AlertRepository alerts,
                         SseBroadcaster sse, org.springframework.transaction.PlatformTransactionManager txManager) {
        this.ai = ai;
        this.runs = runs;
        this.readings = readings;
        this.alerts = alerts;
        this.sse = sse;
        this.txManager = txManager;
    }

    /** Clears live data, opens a new run, and asks the AI service to start replaying. */
    @Transactional
    public AnalysisRun start(double speed, boolean simulate) {
        if (!ai.isUp()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service is not reachable");
        stopQuietly();
        closeRunning(RunStatus.STOPPED);
        clearLiveData();

        AnalysisRun run = new AnalysisRun();
        run.startedAt = OffsetDateTime.now();
        run.speed = speed;
        run.simulate = simulate;
        run.status = RunStatus.RUNNING;
        run = runs.saveAndFlush(run);

        final long runId = run.id;
        // Start only after commit, so the first readings can reference the run row.
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            ai.startReplay(runId, speed, simulate);
                        } catch (RestClientException e) {
                            markFailed(runId);
                            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                    "Replay engine failed to start: " + e.getMessage(), e);
                        }
                    }
                });
        sse.publish("replay", Map.of("type", "started", "runId", runId, "speed", speed, "simulate", simulate));
        return run;
    }

    @Transactional
    public void stop() {
        stopQuietly();
        closeRunning(RunStatus.STOPPED);
        sse.publish("replay", Map.of("type", "stopped"));
    }

    @Transactional
    public void reset() {
        stopQuietly();
        closeRunning(RunStatus.STOPPED);
        clearLiveData();
        sse.publish("replay", Map.of("type", "reset"));
    }

    @Transactional
    public void finished(Long runId) {
        if (runId == null) return;
        runs.findById(runId).ifPresent(run -> {
            if (run.status == RunStatus.RUNNING) {
                run.status = RunStatus.FINISHED;
                run.finishedAt = OffsetDateTime.now();
            }
        });
        sse.publish("replay", Map.of("type", "finished", "runId", runId));
    }

    private void markFailed(long runId) {
        // Runs after the outer commit, so it needs its own transaction to persist.
        new org.springframework.transaction.support.TransactionTemplate(txManager,
                new org.springframework.transaction.support.DefaultTransactionDefinition(
                        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW))
                .executeWithoutResult(s -> runs.findById(runId).ifPresent(run -> {
                    run.status = RunStatus.FAILED;
                    run.finishedAt = OffsetDateTime.now();
                }));
        sse.publish("replay", Map.of("type", "failed", "runId", runId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("aiServiceUp", ai.isUp());
        try {
            out.put("engine", ai.replayStatus());
        } catch (RestClientException e) {
            out.put("engine", Map.of("running", false, "error", "AI service unavailable"));
        }
        out.put("currentRun", runs.findTop20ByOrderByIdDesc().stream().findFirst().orElse(null));
        out.put("readings", readings.count());
        out.put("alerts", alerts.count());
        out.put("unacknowledgedAlerts", alerts.countByAcknowledgedFalse());
        return out;
    }

    @Transactional(readOnly = true)
    public List<AnalysisRun> history() {
        return runs.findTop20ByOrderByIdDesc();
    }

    private void clearLiveData() {
        // measurements, results and alerts are removed by ON DELETE CASCADE
        alerts.deleteAllInBatch();
        readings.deleteAllInBatch();
    }

    private void closeRunning(RunStatus status) {
        runs.findTop20ByOrderByIdDesc().stream().filter(r -> r.status == RunStatus.RUNNING).forEach(r -> {
            r.status = status;
            r.finishedAt = OffsetDateTime.now();
        });
    }

    private void stopQuietly() {
        try {
            ai.stopReplay();
        } catch (RestClientException ignored) {
            // AI service down: nothing is replaying anyway
        }
    }
}
