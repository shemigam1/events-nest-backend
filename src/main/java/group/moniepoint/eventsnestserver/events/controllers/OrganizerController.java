package group.moniepoint.eventsnestserver.events.controllers;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.events.service.EventService;
import group.moniepoint.eventsnestserver.manager.dto.request.AssignManagerRequest;
import group.moniepoint.eventsnestserver.manager.service.ManagerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizer")
@AllArgsConstructor
@Tag(name = "Organizer", description = "Organizer-scoped event management")
public class OrganizerController {

    private final EventService eventService;
    private final AuthService authService;
    private final ManagerService managerService;

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

    @Operation(summary = "Get dashboard stats for the calling organiser",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/stats")
    public ResponseEntity<?> getMyStats(Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(eventService.getMyStats(currentUser));
    }

    // ─── Manager management ───────────────────────────────────────────────────────

    @Operation(summary = "Assign a registered user as manager for an event",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/events/{eventId}/managers")
    public ResponseEntity<?> assignManager(@PathVariable UUID eventId,
                                           @Valid @RequestBody AssignManagerRequest request,
                                           Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(managerService.assignManager(eventId, request, organizer));
    }

    @Operation(summary = "List all managers assigned to an event",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/events/{eventId}/managers")
    public ResponseEntity<?> getManagers(@PathVariable UUID eventId, Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(managerService.getManagersForEvent(eventId, organizer));
    }

    @Operation(summary = "Remove a manager from an event",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/events/{eventId}/managers/{managerId}")
    public ResponseEntity<?> removeManager(@PathVariable UUID eventId,
                                           @PathVariable String managerId,
                                           Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(managerService.removeManager(eventId, managerId, organizer));
    }
}
