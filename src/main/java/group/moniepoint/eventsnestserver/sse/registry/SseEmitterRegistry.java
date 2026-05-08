package group.moniepoint.eventsnestserver.sse.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-user SSE emitter registry. Lives in-memory on a single backend pod —
 * if you horizontally scale beyond one app instance, swap this for a Redis
 * pub/sub-backed dispatcher so events fan out to whichever pod holds the
 * recipient's connection.
 *
 * Caps per-user emitters at {@link #MAX_EMITTERS_PER_USER} to defend against
 * runaway browser tabs.
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    /** 30 minutes — long-lived, keepalive ping every 25s prevents idle proxies from killing it. */
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final int MAX_EMITTERS_PER_USER = 5;

    private final Map<String, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter register(String userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);

        List<SseEmitter> list = emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());

        // If the user already has too many open tabs, evict the oldest to keep memory bounded.
        while (list.size() >= MAX_EMITTERS_PER_USER) {
            SseEmitter evict = list.remove(0);
            try {
                evict.complete();
            } catch (Exception ignored) {
                // already closed; nothing to do
            }
        }

        list.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(ex -> remove(userId, emitter));

        return emitter;
    }

    public void pushTo(String userId, String eventName, Object payload) {
        if (userId == null) return;
        List<SseEmitter> list = emittersByUser.get(userId);
        if (list == null || list.isEmpty()) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE send to {} failed ({}), closing emitter", userId, e.getMessage());
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // emitter callback removes it
                }
            }
        }
    }

    /**
     * Send an SSE comment ("ping") to every connection so intermediaries
     * (load balancers, NAT, browser tab throttlers) keep the TCP socket alive.
     * 25s is comfortably under most idle-connection timeouts (typically 60s).
     */
    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        emittersByUser.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (IOException | IllegalStateException e) {
                    try { emitter.complete(); } catch (Exception ignored) {}
                }
            }
        });
    }

    public int connectedUsers() {
        return emittersByUser.size();
    }

    private void remove(String userId, SseEmitter emitter) {
        List<SseEmitter> list = emittersByUser.get(userId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) {
            emittersByUser.remove(userId);
        }
    }
}
