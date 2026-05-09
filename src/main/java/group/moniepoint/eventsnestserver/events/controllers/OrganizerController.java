package group.moniepoint.eventsnestserver.events.controllers;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.events.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizer")
@AllArgsConstructor
@Tag(name = "Organizer", description = "Organizer-scoped event management")
public class OrganizerController {

    private final EventService eventService;
    private final AuthService authService;

    @Operation(summary = "Get all events created by the calling user",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/events")
    public ResponseEntity<?> getMyEvents(Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(eventService.getMyEvents(currentUser));
    }

    @Operation(summary = "Get a specific event created by the calling user (any status)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/events/{id}")
    public ResponseEntity<?> getMyEventById(@PathVariable UUID id, Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(eventService.getMyEventById(id, currentUser));
    }
}
