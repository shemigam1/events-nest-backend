package group.moniepoint.eventsnestserver.tickets.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.bookings.models.Booking;
import group.moniepoint.eventsnestserver.tickets.dto.response.TicketResponse;
import group.moniepoint.eventsnestserver.tickets.models.Ticket;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;

    /**
     * Seat assignment per PRD §3.4:
     *   index    = countOfNonRefundedTickets + offset
     *   row      = floor(index / seatsPerRow)
     *   position = (index % seatsPerRow) + 1
     *   label    = rowPrefix + (row + 1) + "-" + position
     */
    @Override
    public List<Ticket> issueTickets(Booking booking, TicketTier tier, User attendee, int quantity) {
        long existingCount = ticketRepository.countByTierIdAndStatusIn(
                tier.getId(),
                List.of(TicketStatus.VALID, TicketStatus.USED));

        List<Ticket> tickets = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            long index = existingCount + i;
            int row = (int) (index / tier.getSeatsPerRow());
            int position = (int) (index % tier.getSeatsPerRow()) + 1;
            String seatLabel = tier.getRowPrefix() + (row + 1) + "-" + position;

            tickets.add(Ticket.builder()
                    .booking(booking)
                    .tier(tier)
                    .attendee(attendee)
                    .seatNumber(seatLabel)
                    .qrCode(UUID.randomUUID().toString())
                    .shortCode(NanoIdUtils.randomNanoId(
                            NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
                            NanoIdUtils.DEFAULT_ALPHABET, 8))
                    .status(TicketStatus.VALID)
                    .build());
        }

        return ticketRepository.saveAll(tickets);
    }

    @Override
    public void refundTicketsForBooking(UUID bookingId) {
        List<Ticket> tickets = ticketRepository.findAllByBookingId(bookingId);
        tickets.forEach(t -> t.setStatus(TicketStatus.REFUNDED));
        ticketRepository.saveAll(tickets);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponse> getMyTickets(User user) {
        return ticketRepository.findAllByAttendeeId(user.getId())
                .stream()
                .map(this::toTicketResponse)
                .toList();
    }

    @Override
    public TicketResponse toTicketResponse(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .bookingId(ticket.getBooking() != null ? ticket.getBooking().getId() : null)
                .tierId(ticket.getTier() != null ? ticket.getTier().getId() : null)
                .tierName(ticket.getTier() != null ? ticket.getTier().getName() : null)
                .eventId(ticket.getTier() != null && ticket.getTier().getEvent() != null
                        ? ticket.getTier().getEvent().getId() : null)
                .eventTitle(ticket.getTier() != null && ticket.getTier().getEvent() != null
                        ? ticket.getTier().getEvent().getTitle() : null)
                .eventVenue(ticket.getTier() != null && ticket.getTier().getEvent() != null
                        ? ticket.getTier().getEvent().getVenue() : null)
                .eventStartTime(ticket.getTier() != null && ticket.getTier().getEvent() != null
                        ? ticket.getTier().getEvent().getStartTime() : null)
                .seatNumber(ticket.getSeatNumber())
                .qrCode(ticket.getQrCode())
                .shortCode(ticket.getShortCode())
                .status(ticket.getStatus())
                .checkedInAt(ticket.getCheckedInAt())
                .checkedInByLabel(ticket.getCheckedInByLabel())
                .issuedAt(ticket.getIssuedAt())
                .build();
    }
}
