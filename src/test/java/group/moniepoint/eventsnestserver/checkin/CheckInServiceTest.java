package group.moniepoint.eventsnestserver.checkin;

import group.moniepoint.eventsnestserver.checkin.dto.request.CheckInRequest;
import group.moniepoint.eventsnestserver.checkin.event.TicketCheckedInEvent;
import group.moniepoint.eventsnestserver.checkin.kafka.CheckInEventPublisher;
import group.moniepoint.eventsnestserver.checkin.model.CheckInInvite;
import group.moniepoint.eventsnestserver.checkin.model.CheckInInviteStatus;
import group.moniepoint.eventsnestserver.checkin.port.CheckInTicketView;
import group.moniepoint.eventsnestserver.checkin.port.TicketLookupPort;
import group.moniepoint.eventsnestserver.checkin.repository.CheckInInviteRepository;
import group.moniepoint.eventsnestserver.checkin.service.CheckInServiceImpl;
import group.moniepoint.eventsnestserver.events.models.EventDay;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventDayRepository;
import group.moniepoint.eventsnestserver.exception.checkin.CheckInEventDayRequiredException;
import group.moniepoint.eventsnestserver.exception.checkin.WrongTicketEventDayException;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import group.moniepoint.eventsnestserver.util.TokenHashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    @Mock private TicketLookupPort ticketLookupPort;
    @Mock private CheckInInviteRepository inviteRepository;
    @Mock private CheckInEventPublisher eventPublisher;
    @Mock private EventDayRepository eventDayRepository;

    private CheckInServiceImpl checkInService;

    @BeforeEach
    void setUp() {
        checkInService = new CheckInServiceImpl(
                ticketLookupPort,
                inviteRepository,
                eventPublisher,
                eventDayRepository,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    @Test
    void checkIn_singleDayWholeEventTier_infersSoleEventDay() {
        UUID eventId = UUID.randomUUID();
        UUID dayId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Events event = Events.builder().id(eventId).title("Conf").build();
        CheckInInvite invite = CheckInInvite.builder()
                .event(event)
                .name("Ada")
                .status(CheckInInviteStatus.ACTIVE)
                .expiresAt(now.plusHours(4))
                .tokenHash(TokenHashUtils.hash("staff-secret"))
                .build();

        when(inviteRepository.findByTokenHash(TokenHashUtils.hash("staff-secret")))
                .thenReturn(Optional.of(invite));

        CheckInTicketView ticket = new CheckInTicketView(
                ticketId,
                eventId,
                "Conf",
                "GA",
                "A-1",
                "qr-1",
                TicketStatus.VALID,
                now.minusHours(1),
                null,
                "user-1",
                "Ann",
                "Lee");
        when(ticketLookupPort.findByQrCode("qr-1")).thenReturn(Optional.of(ticket));

        EventDay day = EventDay.builder()
                .id(dayId)
                .dayNumber(1)
                .startTime(now.minusHours(3))
                .endTime(now.plusHours(8))
                .checkInStartTime(now.minusHours(2))
                .build();
        when(eventDayRepository.countByEventId(eventId)).thenReturn(1L);
        when(eventDayRepository.findAllByEventIdOrderByDayNumberAsc(eventId)).thenReturn(List.of(day));
        when(eventDayRepository.findByIdAndEventId(dayId, eventId)).thenReturn(Optional.of(day));
        when(ticketLookupPort.tryRecordCheckIn(
                eq(ticketId), eq("qr-1"), eq(dayId), eq(false), any(), any()))
                .thenReturn(1);
        when(inviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CheckInRequest request = new CheckInRequest();
        request.setStaffToken("staff-secret");
        request.setQrCode("qr-1");

        assertThat(checkInService.checkIn(eventId, request).isSuccess()).isTrue();

        ArgumentCaptor<TicketCheckedInEvent> captor = ArgumentCaptor.forClass(TicketCheckedInEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().eventDayId()).isEqualTo(dayId);
    }

    @Test
    void checkIn_multiDayWholeEventTier_requiresEventDayId() {
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Events event = Events.builder().id(eventId).title("Fest").build();
        CheckInInvite invite = CheckInInvite.builder()
                .event(event)
                .name("Bob")
                .status(CheckInInviteStatus.ACTIVE)
                .expiresAt(now.plusHours(4))
                .tokenHash(TokenHashUtils.hash("tok"))
                .build();
        when(inviteRepository.findByTokenHash(TokenHashUtils.hash("tok"))).thenReturn(Optional.of(invite));

        CheckInTicketView ticket = new CheckInTicketView(
                ticketId, eventId, "Fest", "Pass", "P-1", "qr-2", TicketStatus.VALID,
                now.minusHours(1), null, "u1", "x", "y");
        when(ticketLookupPort.findByQrCode("qr-2")).thenReturn(Optional.of(ticket));
        when(eventDayRepository.countByEventId(eventId)).thenReturn(2L);

        CheckInRequest request = new CheckInRequest();
        request.setStaffToken("tok");
        request.setQrCode("qr-2");

        assertThatThrownBy(() -> checkInService.checkIn(eventId, request))
                .isInstanceOf(CheckInEventDayRequiredException.class);
    }

    @Test
    void checkIn_dayScopedTier_rejectsMismatchedEventDayId() {
        UUID eventId = UUID.randomUUID();
        UUID tierDayId = UUID.randomUUID();
        UUID otherDayId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Events event = Events.builder().id(eventId).title("Fest").build();
        CheckInInvite invite = CheckInInvite.builder()
                .event(event)
                .name("Bob")
                .status(CheckInInviteStatus.ACTIVE)
                .expiresAt(now.plusHours(4))
                .tokenHash(TokenHashUtils.hash("tok"))
                .build();
        when(inviteRepository.findByTokenHash(TokenHashUtils.hash("tok"))).thenReturn(Optional.of(invite));

        CheckInTicketView ticket = new CheckInTicketView(
                ticketId, eventId, "Fest", "Sat only", "S-1", "qr-3", TicketStatus.VALID,
                now.minusHours(1), tierDayId, "u1", "x", "y");
        when(ticketLookupPort.findByQrCode("qr-3")).thenReturn(Optional.of(ticket));

        CheckInRequest request = new CheckInRequest();
        request.setStaffToken("tok");
        request.setQrCode("qr-3");
        request.setEventDayId(otherDayId);

        assertThatThrownBy(() -> checkInService.checkIn(eventId, request))
                .isInstanceOf(WrongTicketEventDayException.class);
    }
}
