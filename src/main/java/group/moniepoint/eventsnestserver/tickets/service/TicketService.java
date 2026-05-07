package group.moniepoint.eventsnestserver.tickets.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.bookings.models.Booking;
import group.moniepoint.eventsnestserver.tickets.dto.response.TicketResponse;
import group.moniepoint.eventsnestserver.tickets.models.Ticket;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;

import java.util.List;
import java.util.UUID;

public interface TicketService {

    /**
     * Issues {@code quantity} tickets for the given booking with sequentially
     * assigned seat numbers. Caller must have already decremented tier capacity.
     */
    List<Ticket> issueTickets(Booking booking, TicketTier tier, User attendee, int quantity);

    void refundTicketsForBooking(UUID bookingId);

    List<TicketResponse> getMyTickets(User user);

    TicketResponse toTicketResponse(Ticket ticket);
}
