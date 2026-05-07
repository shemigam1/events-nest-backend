package group.moniepoint.eventsnestserver.tiers.controllers;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.tiers.dto.request.CreateTicketTierRequest;
import group.moniepoint.eventsnestserver.tiers.dto.request.UpdateTicketTierRequest;
import group.moniepoint.eventsnestserver.tiers.service.TicketTierService;
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
@RequestMapping("/api/v1/events/{eventId}/tiers")
@AllArgsConstructor
@Tag(name = "Ticket Tiers", description = "Tier configuration & seat map management for an event")
public class TicketTierController {

    private final TicketTierService tierService;
    private final AuthService authService;

    @Operation(summary = "Create a ticket tier (organiser only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ResponseEntity<?> createTier(@PathVariable UUID eventId,
                                        @Valid @RequestBody CreateTicketTierRequest request,
                                        Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.status(CREATED).body(tierService.createTier(eventId, request, currentUser));
    }

    @Operation(summary = "List all tiers for an event")
    @GetMapping
    public ResponseEntity<?> getTiers(@PathVariable UUID eventId) {
        return ResponseEntity.ok(tierService.getTiersByEvent(eventId));
    }

    @Operation(summary = "Update tier name or price (organiser only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{tierId}")
    public ResponseEntity<?> updateTier(@PathVariable UUID eventId,
                                        @PathVariable UUID tierId,
                                        @Valid @RequestBody UpdateTicketTierRequest request,
                                        Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(tierService.updateTier(eventId, tierId, request, currentUser));
    }

    @Operation(summary = "Delete a ticket tier (organiser only, no bookings)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{tierId}")
    public ResponseEntity<?> deleteTier(@PathVariable UUID eventId,
                                        @PathVariable UUID tierId,
                                        Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        tierService.deleteTier(eventId, tierId, currentUser);
        return ResponseEntity.status(NO_CONTENT).build();
    }
}
