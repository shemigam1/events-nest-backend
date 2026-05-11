package group.moniepoint.eventsnestserver.events.controllers;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventRequest;
import group.moniepoint.eventsnestserver.events.service.EventService;
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
@RequestMapping("/api/v1/events")
@AllArgsConstructor
@Tag(name = "Events", description = "Event lifecycle management")
public class EventController {

    private final EventService eventService;
    private final AuthService authService;

    @Operation(summary = "Create a new event", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ResponseEntity<?> createEvent(@Valid @RequestBody CreateEventRequest request, Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.status(CREATED).body(eventService.createEvent(request, currentUser));
    }

    @Operation(summary = "Get all published events")
    @GetMapping
    public ResponseEntity<?> getPublishedEvents() {
        return ResponseEntity.ok(eventService.getPublishedEvents());
    }

    @Operation(summary = "Get event details by ID. PRIVATE events return 404 unless caller is the organizer or has an accepted RSVP.")
    @GetMapping("/{id}")
    public ResponseEntity<?> getEventById(@PathVariable UUID id, Principal principal) {
        User caller = principal != null ? authService.findByEmail(principal.getName()) : null;
        return ResponseEntity.ok(eventService.getEventById(id, caller));
    }

    @Operation(summary = "Resolve a short event code to event details (used by check-in scanner login)")
    @GetMapping("/code/{code}")
    public ResponseEntity<?> getEventByCode(@PathVariable String code) {
        return ResponseEntity.ok(eventService.getEventByCode(code));
    }

    @Operation(summary = "Update event (organiser only)", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateEvent(@PathVariable UUID id,
                                         @Valid @RequestBody UpdateEventRequest request,
                                         Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(eventService.updateEvent(id, request, currentUser));
    }

    @Operation(summary = "Submit event for approval (organiser only)", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{id}/submit")
    public ResponseEntity<?> submitForApproval(@PathVariable UUID id, Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(eventService.submitForApproval(id, currentUser));
    }

    @Operation(summary = "Delete event (organiser only, DRAFT or CANCELLED)", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEvent(@PathVariable UUID id, Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        eventService.deleteEvent(id, currentUser);
        return ResponseEntity.status(NO_CONTENT).build();
    }
}
