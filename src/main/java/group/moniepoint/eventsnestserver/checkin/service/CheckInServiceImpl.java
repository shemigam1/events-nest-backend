package group.moniepoint.eventsnestserver.checkin.service;

import group.moniepoint.eventsnestserver.checkin.dto.request.CheckInRequest;
import group.moniepoint.eventsnestserver.checkin.dto.response.CheckInResponse;
import group.moniepoint.eventsnestserver.checkin.event.TicketCheckedInEvent;
import group.moniepoint.eventsnestserver.checkin.kafka.CheckInEventPublisher;
import group.moniepoint.eventsnestserver.checkin.model.CheckInInvite;
import group.moniepoint.eventsnestserver.checkin.model.CheckInInviteStatus;
import group.moniepoint.eventsnestserver.checkin.port.CheckInTicketView;
import group.moniepoint.eventsnestserver.checkin.port.TicketLookupPort;
import group.moniepoint.eventsnestserver.checkin.repository.CheckInInviteRepository;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.CheckInNotYetOpenException;
import group.moniepoint.eventsnestserver.exception.InvalidCheckInTokenException;
import group.moniepoint.eventsnestserver.exception.TicketAlreadyUsedException;
import group.moniepoint.eventsnestserver.exception.TicketNotFoundException;
import group.moniepoint.eventsnestserver.exception.TicketRefundedException;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import group.moniepoint.eventsnestserver.util.TokenHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    private final TicketLookupPort ticketLookupPort;
    private final CheckInInviteRepository inviteRepository;
    private final CheckInEventPublisher eventPublisher;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Override
    @Transactional
    public EventsNestResponse<CheckInResponse> checkIn(UUID eventId, CheckInRequest request) {
        // 1. Validate staff token
        String tokenHash = TokenHashUtils.hash(request.getStaffToken());
        CheckInInvite invite = inviteRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidCheckInTokenException::new);

        if (invite.getStatus() != CheckInInviteStatus.ACTIVE
                || invite.getExpiresAt().isBefore(LocalDateTime.now())
                || !invite.getEvent().getId().equals(eventId)) {
            throw new InvalidCheckInTokenException();
        }

        // 2. Look up ticket (cache-backed)
        CheckInTicketView ticket = ticketLookupPort.findByQrCode(request.getQrCode())
                .orElseThrow(TicketNotFoundException::new);

        if (!ticket.eventId().equals(eventId)) {
            throw new TicketNotFoundException();
        }

        // 3. Enforce check-in window
        if (ticket.checkInStartTime() != null
                && LocalDateTime.now().isBefore(ticket.checkInStartTime())) {
            throw new CheckInNotYetOpenException(ticket.checkInStartTime());
        }

        // 5. Validate ticket status before attempting write
        if (ticket.status() == TicketStatus.REFUNDED) {
            throw new TicketRefundedException();
        }
        if (ticket.status() == TicketStatus.USED) {
            throw new TicketAlreadyUsedException();
        }

        // 6. Optimistic check-in — UPDATE WHERE status = VALID
        String label = invite.getName() + " (staff)";
        LocalDateTime now = LocalDateTime.now();
        int updated = ticketLookupPort.markCheckedIn(ticket.id(), ticket.qrCode(), now, label);

        if (updated == 0) {
            // Another request won the race
            throw new TicketAlreadyUsedException();
        }

        // 5. Record last usage on the invite
        invite.setLastUsedAt(now);
        inviteRepository.save(invite);

        // 6. Publish audit event
        eventPublisher.publish(new TicketCheckedInEvent(
                ticket.id(),
                eventId,
                ticket.eventTitle(),
                ticket.attendeeId(),
                ticket.qrCode(),
                ticket.seatNumber(),
                label,
                now));

        // Phase B telemetry — feeds the check-ins/min panel.
        meterRegistry.counter("eventsnest.checkins.total").increment();

        CheckInResponse data = CheckInResponse.builder()
                .ticketId(ticket.id())
                .seatNumber(ticket.seatNumber())
                .tierName(ticket.tierName())
                .eventId(ticket.eventId())
                .eventTitle(ticket.eventTitle())
                .attendeeFirstName(ticket.attendeeFirstName())
                .attendeeLastName(ticket.attendeeLastName())
                .checkedInAt(now)
                .checkedInByLabel(label)
                .build();

        EventsNestResponse<CheckInResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Check-in successful");
        response.setData(data);
        return response;
    }
}
