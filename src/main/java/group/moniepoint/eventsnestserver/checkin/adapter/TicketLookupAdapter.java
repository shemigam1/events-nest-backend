package group.moniepoint.eventsnestserver.checkin.adapter;

import group.moniepoint.eventsnestserver.checkin.port.CheckInTicketView;
import group.moniepoint.eventsnestserver.checkin.port.TicketLookupPort;
import group.moniepoint.eventsnestserver.tickets.models.Ticket;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TicketLookupAdapter implements TicketLookupPort {

    private final TicketRepository ticketRepository;

    @Override
    @Cacheable(value = "tickets-by-qr", key = "#qrCode")
    public Optional<CheckInTicketView> findByQrCode(String qrCode) {
        return ticketRepository.findByQrCode(qrCode).map(this::toView);
    }

    @Override
    @CacheEvict(value = "tickets-by-qr", key = "#qrCode")
    public int markCheckedIn(UUID ticketId, String qrCode, LocalDateTime checkedInAt, String checkedInByLabel) {
        return ticketRepository.markAsCheckedIn(
                ticketId, checkedInAt, checkedInByLabel,
                TicketStatus.USED, TicketStatus.VALID);
    }

    private CheckInTicketView toView(Ticket ticket) {
        return new CheckInTicketView(
                ticket.getId(),
                ticket.getTier().getEvent().getId(),
                ticket.getTier().getEvent().getTitle(),
                ticket.getTier().getName(),
                ticket.getSeatNumber(),
                ticket.getQrCode(),
                ticket.getStatus(),
                ticket.getTier().getEvent().getCheckInStartTime(),
                ticket.getAttendee().getFirstName(),
                ticket.getAttendee().getLastName()
        );
    }
}
