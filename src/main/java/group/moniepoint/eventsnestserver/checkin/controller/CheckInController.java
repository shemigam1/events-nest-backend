package group.moniepoint.eventsnestserver.checkin.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.checkin.dto.request.CheckInRequest;
import group.moniepoint.eventsnestserver.checkin.dto.request.CreateCheckInInviteRequest;
import group.moniepoint.eventsnestserver.checkin.service.CheckInInviteService;
import group.moniepoint.eventsnestserver.checkin.service.CheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/checkin")
@RequiredArgsConstructor
@Tag(name = "Check-In", description = "Event check-in and staff invite management")
public class CheckInController {

    private final CheckInService checkInService;
    private final CheckInInviteService checkInInviteService;
    private final AuthService authService;

    @Operation(summary = "Check in an attendee by QR code (staff token auth, no JWT required)")
    @PostMapping
    public ResponseEntity<?> checkIn(@PathVariable UUID eventId,
                                     @Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.ok(checkInService.checkIn(eventId, request));
    }

    @Operation(summary = "Create a check-in staff invite (organizer only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/invites")
    public ResponseEntity<?> createInvite(@PathVariable UUID eventId,
                                          @Valid @RequestBody CreateCheckInInviteRequest request,
                                          Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(checkInInviteService.createInvite(eventId, request, organizer));
    }

    @Operation(summary = "List all check-in staff invites for an event (organizer only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/invites")
    public ResponseEntity<?> listInvites(@PathVariable UUID eventId,
                                         Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(checkInInviteService.listInvites(eventId, organizer));
    }

    @Operation(summary = "Revoke a check-in staff invite (organizer only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/invites/{inviteId}")
    public ResponseEntity<?> revokeInvite(@PathVariable UUID eventId,
                                           @PathVariable UUID inviteId,
                                           Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(checkInInviteService.revokeInvite(eventId, inviteId, organizer));
    }
}
