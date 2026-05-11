package group.moniepoint.eventsnestserver.bookings.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.bookings.dto.request.CreateBookingRequest;
import group.moniepoint.eventsnestserver.bookings.dto.response.BookingResponse;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;

import java.util.List;
import java.util.UUID;

public interface BookingService {

    /**
     * Reserves tier capacity and creates a booking with paymentStatus=PENDING.
     * No tickets are issued yet — those wait for payment confirmation via
     * {@link #finalizeBookingPayment} (called from the Monnify webhook or
     * /payments/monnify/verify). The response carries a {@code paymentUrl}
     * the frontend redirects to.
     */
    EventsNestResponse<BookingResponse> createBooking(UUID eventId, CreateBookingRequest request, User attendee);

    /**
     * Look up the booking by Monnify transaction reference, verify payment,
     * and on PAID — flip the booking, issue tickets, publish Kafka, count
     * metrics. Idempotent: a second call on an already-PAID booking is a
     * no-op (returns the same response).
     */
    EventsNestResponse<BookingResponse> finalizeBookingPayment(String monnifyTransactionRef);

    /**
     * Mark a PENDING booking as FAILED. Restores tier capacity. Called from
     * the webhook on failure events, and (later) from a TTL sweeper for
     * abandoned bookings.
     */
    EventsNestResponse<BookingResponse> markBookingFailed(String monnifyTransactionRef, String reason);

    EventsNestResponse<BookingResponse> cancelBooking(UUID eventId, UUID bookingId, User requestingUser);

    List<BookingResponse> getMyBookings(User user);

    /**
     * Returns every booking made for the given event. Caller must be an
     * organiser of that event (membership-checked); throws
     * UnauthorizedException otherwise.
     */
    List<BookingResponse> getBookingsForEventByOrganiser(UUID eventId, User requestingUser);
}
