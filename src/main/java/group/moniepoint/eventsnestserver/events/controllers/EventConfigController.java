package group.moniepoint.eventsnestserver.events.controllers;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventConfigRequest;
import group.moniepoint.eventsnestserver.events.service.EventConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/config")
@AllArgsConstructor
@Tag(name = "Event Config", description = "Per-event module toggles that drive the workspace UI")
public class EventConfigController {

    private final EventConfigService configService;
    private final AuthService authService;

    @Operation(summary = "Get the module toggles for an event")
    @GetMapping
    public ResponseEntity<?> getConfig(@PathVariable UUID eventId) {
        return ResponseEntity.ok(configService.getByEventId(eventId));
    }

    @Operation(
            summary = "Update module toggles for an event (organiser only). PATCH semantics — only fields present are changed.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping
    public ResponseEntity<?> updateConfig(@PathVariable UUID eventId,
                                          @Valid @RequestBody UpdateEventConfigRequest request,
                                          Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(configService.updateByEventId(eventId, request, currentUser));
    }
}
