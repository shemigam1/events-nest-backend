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
import group.moniepoint.eventsnestserver.events.models.EventDay;
import group.moniepoint.eventsnestserver.events.repository.EventDayRepository;
import group.moniepoint.eventsnestserver.exception.checkin.CheckInClosedForDayException;
import group.moniepoint.eventsnestserver.exception.checkin.CheckInEventDayRequiredException;
import group.moniepoint.eventsnestserver.exception.checkin.CheckInNotYetOpenException;
import group.moniepoint.eventsnestserver.exception.checkin.InvalidCheckInTokenException;
import group.moniepoint.eventsnestserver.exception.checkin.WrongTicketEventDayException;
import group.moniepoint.eventsnestserver.exception.event.EventDayNotFoundException;
import group.moniepoint.eventsnestserver.exception.ticket.TicketAlreadyUsedException;
import group.moniepoint.eventsnestserver.exception.ticket.TicketNotFoundException;
import group.moniepoint.eventsnestserver.exception.ticket.TicketRefundedException;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import group.moniepoint.eventsnestserver.util.TokenHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    private final TicketLookupPort ticketLookupPort;
    private final CheckInInviteRepository inviteRepository;
    private final CheckInEventPublisher eventPublisher;
    private final EventDayRepository eventDayRepository;
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

        // 2. Look up ticket — QR scan (primary) or short code (manual fallback)
        if (request.getQrCode() == null && request.getShortCode() == null) {
            throw new TicketNotFoundException();
        }
        CheckInTicketView ticket = (request.getQrCode() != null
                ? ticketLookupPort.findByQrCode(request.getQrCode())
                : ticketLookupPort.findByShortCode(request.getShortCode()))
                .orElseThrow(TicketNotFoundException::new);

        if (!ticket.eventId().equals(eventId)) {
            throw new TicketNotFoundException();
        }

        if (ticket.status() == TicketStatus.REFUNDED) {
            throw new TicketRefundedException();
        }
        boolean dayScopedTier = ticket.tierEventDayId() != null;
        if (ticket.status() == TicketStatus.USED && dayScopedTier) {
            throw new TicketAlreadyUsedException();
        }
        if (ticket.status() == TicketStatus.USED) {
            // Legacy whole-event ticket that was marked USED under the old single check-in model.
            throw new TicketAlreadyUsedException();
        }

        UUID resolvedEventDayId = resolveCheckInEventDayId(request, ticket, eventId);
        EventDay day = eventDayRepository.findByIdAndEventId(resolvedEventDayId, eventId)
                .orElseThrow(EventDayNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(day.getEndTime())) {
            throw new CheckInClosedForDayException(day.getEndTime());
        }

        LocalDateTime opensAt = day.getCheckInStartTime() != null
                ? day.getCheckInStartTime()
                : ticket.eventCheckInStartTime();
        if (opensAt != null && now.isBefore(opensAt)) {
            throw new CheckInNotYetOpenException(opensAt);
        }

        // Record check-in (idempotent per ticket + day)
        String label = invite.getName() + " (staff)";
        int recorded = ticketLookupPort.tryRecordCheckIn(
                ticket.id(),
                ticket.qrCode(),
                resolvedEventDayId,
                dayScopedTier,
                now,
                label);

        if (recorded == 0) {
            throw new TicketAlreadyUsedException();
        }

        invite.setLastUsedAt(now);
        inviteRepository.save(invite);

        eventPublisher.publish(new TicketCheckedInEvent(
                ticket.id(),
                eventId,
                resolvedEventDayId,
                ticket.eventTitle(),
                ticket.attendeeId(),
                ticket.qrCode(),
                ticket.seatNumber(),
                label,
                now));

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
                .eventDayId(resolvedEventDayId)
                .build();

        EventsNestResponse<CheckInResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Check-in successful");
        response.setData(data);
        return response;
    }

    private UUID resolveCheckInEventDayId(CheckInRequest request, CheckInTicketView ticket, UUID eventId) {
        UUID requested = request.getEventDayId();
        UUID tierDayId = ticket.tierEventDayId();

        if (tierDayId != null) {
            if (requested != null && !requested.equals(tierDayId)) {
                throw new WrongTicketEventDayException();
            }
            return tierDayId;
        }

        long dayCount = eventDayRepository.countByEventId(eventId);
        if (dayCount <= 1) {
            List<EventDay> days = eventDayRepository.findAllByEventIdOrderByDayNumberAsc(eventId);
            if (days.isEmpty()) {
                throw new EventDayNotFoundException();
            }
            EventDay only = days.getFirst();
            if (requested != null && !requested.equals(only.getId())) {
                throw new EventDayNotFoundException();
            }
            return only.getId();
        }

        if (requested == null) {
            throw new CheckInEventDayRequiredException();
        }
        return eventDayRepository.findByIdAndEventId(requested, eventId)
                .map(EventDay::getId)
                .orElseThrow(EventDayNotFoundException::new);
    }
}
