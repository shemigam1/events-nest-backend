package group.moniepoint.eventsnestserver.sse.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.sse.registry.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sse")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Server-Sent Events", description = "Server-pushed state changes scoped to the authenticated user")
public class SseController {

    private final SseEmitterRegistry registry;
    private final AuthService authService;

    /**
     * Long-lived SSE stream for the authenticated user. The frontend opens
     * this once on login (via {@code @microsoft/fetch-event-source} so it can
     * carry the JWT in the Authorization header) and closes on logout.
     *
     * Events delivered on this stream:
     *   - booking.confirmed
     *   - event.approved
     *   - event.rejected
     *   - ticket.checked-in
     */
    @Operation(
            summary = "Open the SSE stream for the calling user",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(Principal principal) {
        User user = authService.findByEmail(principal.getName());
        SseEmitter emitter = registry.register(user.getId());

        // Send a "connected" handshake immediately so clients can confirm the
        // pipe is open without waiting for the first real event.
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("userId", user.getId())));
        } catch (IOException e) {
            log.debug("Initial SSE handshake failed for {}: {}", user.getId(), e.getMessage());
            emitter.complete();
        }

        return emitter;
    }
}
