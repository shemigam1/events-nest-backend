package group.moniepoint.eventsnestserver.events.controllers;

import group.moniepoint.eventsnestserver.events.service.EventService;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventRequest;
import group.moniepoint.eventsnestserver.user.User;
import group.moniepoint.eventsnestserver.user.UserService;
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
@RequestMapping("/api/events")
@AllArgsConstructor
public class EventController {

    private final EventService eventService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> createEvent(@Valid @RequestBody CreateEventRequest request, Principal principal) {
        User currentUser = userService.findByEmail(principal.getName());
        return ResponseEntity.status(CREATED).body(eventService.createEvent(request, currentUser));
    }

    @GetMapping
    public ResponseEntity<?> getPublishedEvents() {
        return ResponseEntity.ok(eventService.getPublishedEvents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getEventById(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateEvent(@PathVariable UUID id,
                                         @Valid @RequestBody UpdateEventRequest request,
                                         Principal principal) {
        User currentUser = userService.findByEmail(principal.getName());
        return ResponseEntity.ok(eventService.updateEvent(id, request, currentUser));
    }

    @PatchMapping("/{id}/submit")
    public ResponseEntity<?> submitForApproval(@PathVariable UUID id, Principal principal) {
        User currentUser = userService.findByEmail(principal.getName());
        return ResponseEntity.ok(eventService.submitForApproval(id, currentUser));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEvent(@PathVariable UUID id, Principal principal) {
        User currentUser = userService.findByEmail(principal.getName());
        eventService.deleteEvent(id, currentUser);
        return ResponseEntity.status(NO_CONTENT).build();
    }
}
