package space.astra.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Pushes live events to connected dashboards (Server-Sent Events). */
@Component
public class SseBroadcaster {
    private static final Logger log = LoggerFactory.getLogger(SseBroadcaster.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        send(emitter, "hello", java.util.Map.of("connected", true));
        return emitter;
    }

    /** Send now, or right after the current transaction commits so clients never read stale data. */
    public void publish(String event, Object data) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast(event, data);
                }
            });
        } else {
            broadcast(event, data);
        }
    }

    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        broadcast("ping", java.util.Map.of("t", System.currentTimeMillis()));
    }

    private void broadcast(String event, Object data) {
        for (SseEmitter emitter : emitters) {
            send(emitter, event, data);
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            log.debug("dropping SSE client: {}", e.getMessage());
            emitters.remove(emitter);
        }
    }
}
