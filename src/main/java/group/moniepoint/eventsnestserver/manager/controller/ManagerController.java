package group.moniepoint.eventsnestserver.manager.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.manager.service.ManagerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/**
 * Panel endpoints for users who are assigned as MANAGER on one or more events.
 * A manager can view all events they manage and drill into each one's detail —
 * same data shape as the organiser view.
 */
@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
@Tag(name = "Manager Panel", description = "Manager-scoped event access")
public class ManagerController {

    private final ManagerService managerService;
    private final AuthService authService;

    @Operation(summary = "List all events the calling user is managing",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/events")
    public ResponseEntity<?> getManagedEvents(Principal principal) {
        User manager = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(managerService.getManagedEvents(manager));
    }

    @Operation(summary = "Get a single managed event by id",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/events/{eventId}")
    public ResponseEntity<?> getManagedEvent(@PathVariable UUID eventId, Principal principal) {
        User manager = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(managerService.getManagedEventById(eventId, manager));
    }
}
