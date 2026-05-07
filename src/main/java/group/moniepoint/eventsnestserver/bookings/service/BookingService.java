package group.moniepoint.eventsnestserver.bookings.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.bookings.dto.request.CreateBookingRequest;
import group.moniepoint.eventsnestserver.bookings.dto.response.BookingResponse;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;

import java.util.List;
import java.util.UUID;

public interface BookingService {

    EventsNestResponse<BookingResponse> createBooking(UUID eventId, CreateBookingRequest request, User attendee);

    EventsNestResponse<BookingResponse> cancelBooking(UUID eventId, UUID bookingId, User requestingUser);

    List<BookingResponse> getMyBookings(User user);
}
