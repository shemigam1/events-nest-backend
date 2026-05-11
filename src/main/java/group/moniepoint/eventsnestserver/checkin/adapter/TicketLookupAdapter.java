package group.moniepoint.eventsnestserver.checkin.adapter;

import group.moniepoint.eventsnestserver.checkin.port.CheckInTicketView;
import group.moniepoint.eventsnestserver.checkin.port.TicketLookupPort;
import group.moniepoint.eventsnestserver.checkin.repository.TicketCheckInRepository;
import group.moniepoint.eventsnestserver.tickets.models.Ticket;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import group.moniepoint.eventsnestserver.exception.ticket.TicketAlreadyUsedException;
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
    private final TicketCheckInRepository ticketCheckInRepository;

    @Override
    @Cacheable(value = "tickets-by-qr", key = "#qrCode")
    public Optional<CheckInTicketView> findByQrCode(String qrCode) {
        return ticketRepository.findByQrCode(qrCode).map(this::toView);
    }

    @Override
    public Optional<CheckInTicketView> findByShortCode(String shortCode) {
        return ticketRepository.findByShortCode(shortCode).map(this::toView);
    }

    @Override
    @CacheEvict(value = "tickets-by-qr", key = "#qrCode")
    public int tryRecordCheckIn(UUID ticketId,
                                String qrCode,
                                UUID eventDayId,
                                boolean dayScopedTier,
                                LocalDateTime checkedInAt,
                                String checkedInByLabel) {
        int inserted = ticketCheckInRepository.insertIfAbsent(
                UUID.randomUUID(), ticketId, eventDayId, checkedInAt, checkedInByLabel);
        if (inserted == 0) {
            return 0;
        }
        if (dayScopedTier) {
            int updated = ticketRepository.markAsCheckedIn(
                    ticketId, checkedInAt, checkedInByLabel,
                    TicketStatus.USED, TicketStatus.VALID);
            if (updated == 0) {
                throw new TicketAlreadyUsedException();
            }
            return 1;
        }
        int updated = ticketRepository.updateLastCheckInForValidPass(
                ticketId, checkedInAt, checkedInByLabel, TicketStatus.VALID);
        if (updated == 0) {
            throw new TicketAlreadyUsedException();
        }
        return 1;
    }

    private CheckInTicketView toView(Ticket ticket) {
        UUID tierDayId = ticket.getTier().getEventDay() != null
                ? ticket.getTier().getEventDay().getId()
                : null;
        return new CheckInTicketView(
                ticket.getId(),
                ticket.getTier().getEvent().getId(),
                ticket.getTier().getEvent().getTitle(),
                ticket.getTier().getName(),
                ticket.getSeatNumber(),
                ticket.getQrCode(),
                ticket.getStatus(),
                ticket.getTier().getEvent().getCheckInStartTime(),
                tierDayId,
                ticket.getAttendee().getId(),
                ticket.getAttendee().getFirstName(),
                ticket.getAttendee().getLastName()
        );
    }
}
