package group.moniepoint.eventsnestserver.guestlist.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.guestlist.dto.request.CreateGuestRequest;
import group.moniepoint.eventsnestserver.guestlist.dto.request.RsvpRequest;
import group.moniepoint.eventsnestserver.guestlist.dto.request.UpdateGuestStatusRequest;
import group.moniepoint.eventsnestserver.guestlist.service.GuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;

@RestController
@AllArgsConstructor
@Tag(name = "Guest List", description = "Guest list & RSVP management")
public class GuestController {

    private final GuestService guestService;
    private final AuthService authService;

    @Operation(summary = "Add a guest to the event guest list",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/events/{eventId}/guests")
    public ResponseEntity<?> addGuest(@PathVariable UUID eventId,
                                      @Valid @RequestBody CreateGuestRequest request,
                                      Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(guestService.addGuest(eventId, request, organizer));
    }

    @Operation(summary = "List all guests for an event",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/events/{eventId}/guests")
    public ResponseEntity<?> getGuests(@PathVariable UUID eventId, Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(guestService.getGuests(eventId, organizer));
    }

    @Operation(summary = "Manually update a guest's RSVP status",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/v1/events/{eventId}/guests/{guestId}/status")
    public ResponseEntity<?> updateGuestStatus(@PathVariable UUID eventId,
                                               @PathVariable UUID guestId,
                                               @Valid @RequestBody UpdateGuestStatusRequest request,
                                               Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(guestService.updateGuestStatus(eventId, guestId, request, organizer));
    }

    @Operation(summary = "Remove a guest from the event",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/api/v1/events/{eventId}/guests/{guestId}")
    public ResponseEntity<?> removeGuest(@PathVariable UUID eventId,
                                         @PathVariable UUID guestId,
                                         Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(guestService.removeGuest(eventId, guestId, organizer));
    }

    @Operation(summary = "Submit RSVP response via token (public — used from email link)")
    @PostMapping("/api/v1/rsvp")
    public ResponseEntity<?> rsvp(@Valid @RequestBody RsvpRequest request) {
        return ResponseEntity.ok(guestService.rsvp(request));
    }

    @Operation(summary = "Export guest list as CSV",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/events/{eventId}/guests/export")
    public ResponseEntity<byte[]> exportGuestList(@PathVariable UUID eventId, Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        // permission check via getGuests (also asserts organizer + module enabled)
        var guests = guestService.getGuests(eventId, organizer);

        StringBuilder csv = new StringBuilder();
        csv.append("Guest Name,Email,Phone,RSVP Status,Invited At,Responded At,Note\n");
        for (var g : guests) {
            csv.append(escape(g.getName())).append(',')
               .append(escape(g.getEmail())).append(',')
               .append(escape(g.getPhone())).append(',')
               .append(g.getRsvpStatus()).append(',')
               .append(g.getInvitedAt()).append(',')
               .append(g.getRespondedAt() != null ? g.getRespondedAt() : "").append(',')
               .append(escape(g.getNote())).append('\n');
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"guests.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    private static String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
