package group.moniepoint.eventsnestserver.bookings.controllers;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.bookings.dto.request.CreateBookingRequest;
import group.moniepoint.eventsnestserver.bookings.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@AllArgsConstructor
@Tag(name = "Bookings", description = "Ticket booking, cancellation, and user bookings")
public class BookingController {

    private final BookingService bookingService;
    private final AuthService authService;

    @Operation(summary = "Book tickets for an event",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/events/{eventId}/bookings")
    public ResponseEntity<?> createBooking(@PathVariable UUID eventId,
                                           @Valid @RequestBody CreateBookingRequest request,
                                           Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.status(CREATED)
                .body(bookingService.createBooking(eventId, request, currentUser));
    }

    @Operation(summary = "Cancel a booking (attendee only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/events/{eventId}/bookings/{bookingId}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable UUID eventId,
                                           @PathVariable UUID bookingId,
                                           Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(bookingService.cancelBooking(eventId, bookingId, currentUser));
    }

    @Operation(summary = "Get all bookings for the calling user",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/me/bookings")
    public ResponseEntity<?> getMyBookings(Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(bookingService.getMyBookings(currentUser));
    }
}
