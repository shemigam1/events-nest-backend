package group.moniepoint.eventsnestserver.bookings.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.bookings.dto.request.CreateBookingRequest;
import group.moniepoint.eventsnestserver.bookings.dto.response.BookingResponse;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;

import java.util.List;
import java.util.UUID;

public interface BookingService {

    EventsNestResponse<BookingResponse> createBooking(UUID eventId, CreateBookingRequest request, User attendee);

    /**
     * Look up the booking by payment gateway reference, flip it to PAID, issue
     * tickets, and publish the confirmed Kafka event. Idempotent — a second call
     * on an already-PAID booking is a no-op.
     */
    EventsNestResponse<BookingResponse> finalizeBookingPayment(String paymentGatewayRef);

    /**
     * Mark a PENDING booking as FAILED and restore tier capacity. Called from
     * a payment gateway callback on failure events, or from a TTL sweeper for
     * abandoned bookings.
     */
    EventsNestResponse<BookingResponse> markBookingFailed(String paymentGatewayRef, String reason);

    EventsNestResponse<BookingResponse> cancelBooking(UUID eventId, UUID bookingId, User requestingUser);

    List<BookingResponse> getMyBookings(User user);

    /**
     * Returns every booking made for the given event. Caller must be an
     * organiser of that event (membership-checked); throws
     * UnauthorizedException otherwise.
     */
    List<BookingResponse> getBookingsForEventByOrganiser(UUID eventId, User requestingUser);
}
