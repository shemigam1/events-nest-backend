package group.moniepoint.eventsnestserver.events.controllers;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventDayRequest;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventDayRequest;
import group.moniepoint.eventsnestserver.events.service.EventDayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/v1/events/{eventId}/days")
@AllArgsConstructor
@Tag(name = "Event Days", description = "Multi-day event structure. Single-day events use the auto-created day implicitly.")
public class EventDayController {

    private final EventDayService dayService;
    private final AuthService authService;

    @Operation(summary = "List days for an event, ordered by day number")
    @GetMapping
    public ResponseEntity<?> listDays(@PathVariable UUID eventId) {
        return ResponseEntity.ok(dayService.listByEventId(eventId));
    }

    @Operation(summary = "Add a new day to a multi-day event (organiser only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ResponseEntity<?> addDay(@PathVariable UUID eventId,
                                    @Valid @RequestBody CreateEventDayRequest request,
                                    Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.status(CREATED).body(dayService.addDay(eventId, request, currentUser));
    }

    @Operation(summary = "Update day fields (organiser only). PATCH semantics.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{dayId}")
    public ResponseEntity<?> updateDay(@PathVariable UUID eventId,
                                       @PathVariable UUID dayId,
                                       @Valid @RequestBody UpdateEventDayRequest request,
                                       Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(dayService.updateDay(eventId, dayId, request, currentUser));
    }

    @Operation(summary = "Delete a day (organiser only). Cannot delete the last remaining day.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{dayId}")
    public ResponseEntity<?> deleteDay(@PathVariable UUID eventId,
                                       @PathVariable UUID dayId,
                                       Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        dayService.deleteDay(eventId, dayId, currentUser);
        return ResponseEntity.status(NO_CONTENT).build();
    }
}
