package group.moniepoint.eventsnestserver.events;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventDayRequest;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventDayRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventDayResponse;
import group.moniepoint.eventsnestserver.events.models.EventDay;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventDayRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.events.service.EventDayServiceImpl;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.checkin.InvalidCheckInStartTimeException;
import group.moniepoint.eventsnestserver.exception.event.CannotDeleteLastEventDayException;
import group.moniepoint.eventsnestserver.exception.event.EventDayNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventDayServiceTest {

    @Mock
    private EventDayRepository dayRepository;

    @Mock
    private EventMembershipRepository membershipRepository;

    @Mock
    private EventRespository eventRepository;

    private EventDayServiceImpl dayService;

    private Events event;
    private User organizer;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        dayService = new EventDayServiceImpl(dayRepository, membershipRepository, eventRepository);

        eventId = UUID.randomUUID();
        event = Events.builder()
                .id(eventId)
                .title("Test Event")
                .venue("Lagos Hall")
                .startTime(LocalDateTime.of(2026, 6, 1, 10, 0))
                .endTime(LocalDateTime.of(2026, 6, 1, 18, 0))
                .checkInStartTime(LocalDateTime.of(2026, 6, 1, 8, 0))
                .build();

        organizer = User.builder()
                .id("organizer001")
                .email("organizer@example.com")
                .firstName("Org")
                .lastName("Anizer")
                .passwordHash("hashed")
                .role(Role.USER)
                .build();
    }

    // ─── createDefaultDayFor ────────────────────────────────────────────────────

    @Test
    void createDefaultDayForMirrorsEventWindow() {
        when(dayRepository.saveAndFlush(any(EventDay.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EventDay result = dayService.createDefaultDayFor(event);

        ArgumentCaptor<EventDay> captor = ArgumentCaptor.forClass(EventDay.class);
        verify(dayRepository).saveAndFlush(captor.capture());
        EventDay persisted = captor.getValue();

        assertThat(persisted.getEvent()).isEqualTo(event);
        assertThat(persisted.getDayNumber()).isEqualTo(1);
        assertThat(persisted.getStartTime()).isEqualTo(event.getStartTime());
        assertThat(persisted.getEndTime()).isEqualTo(event.getEndTime());
        assertThat(persisted.getCheckInStartTime()).isEqualTo(event.getCheckInStartTime());
        assertThat(persisted.getVenue()).isNull();
        assertThat(result).isSameAs(persisted);
    }

    // ─── addDay ─────────────────────────────────────────────────────────────────

    @Test
    void addDayAssignsNextDayNumberAndPersists() {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, organizer.getId(), EventRole.ORGANIZER)).thenReturn(true);
        EventDay existingLast = EventDay.builder().event(event).dayNumber(2).build();
        when(dayRepository.findFirstByEventIdOrderByDayNumberDesc(eventId))
                .thenReturn(Optional.of(existingLast));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(dayRepository.saveAndFlush(any(EventDay.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateEventDayRequest request = new CreateEventDayRequest();
        request.setTitle("Day 3 — Wrap-up");
        request.setStartTime(LocalDateTime.of(2026, 6, 3, 9, 0));
        request.setEndTime(LocalDateTime.of(2026, 6, 3, 17, 0));
        request.setCheckInStartTime(LocalDateTime.of(2026, 6, 3, 8, 0));
        request.setVenue("Side Hall");

        EventsNestResponse<EventDayResponse> result = dayService.addDay(eventId, request, organizer);

        ArgumentCaptor<EventDay> captor = ArgumentCaptor.forClass(EventDay.class);
        verify(dayRepository).saveAndFlush(captor.capture());
        EventDay persisted = captor.getValue();

        assertThat(persisted.getDayNumber()).isEqualTo(3);
        assertThat(persisted.getTitle()).isEqualTo("Day 3 — Wrap-up");
        assertThat(persisted.getVenue()).isEqualTo("Side Hall");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getDayNumber()).isEqualTo(3);
    }

    @Test
    void addDayUsesOneAsDayNumberWhenNoExistingDays() {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, organizer.getId(), EventRole.ORGANIZER)).thenReturn(true);
        when(dayRepository.findFirstByEventIdOrderByDayNumberDesc(eventId))
                .thenReturn(Optional.empty());
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(dayRepository.saveAndFlush(any(EventDay.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateEventDayRequest request = new CreateEventDayRequest();
        request.setStartTime(LocalDateTime.of(2026, 6, 1, 9, 0));
        request.setEndTime(LocalDateTime.of(2026, 6, 1, 17, 0));

        dayService.addDay(eventId, request, organizer);

        ArgumentCaptor<EventDay> captor = ArgumentCaptor.forClass(EventDay.class);
        verify(dayRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getDayNumber()).isEqualTo(1);
    }

    @Test
    void addDayThrowsWhenCallerIsNotOrganizer() {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, organizer.getId(), EventRole.ORGANIZER)).thenReturn(false);

        CreateEventDayRequest request = new CreateEventDayRequest();
        request.setStartTime(LocalDateTime.of(2026, 6, 1, 9, 0));
        request.setEndTime(LocalDateTime.of(2026, 6, 1, 17, 0));

        assertThatThrownBy(() -> dayService.addDay(eventId, request, organizer))
                .isInstanceOf(NotEventOrganizerException.class);
        verify(dayRepository, never()).saveAndFlush(any(EventDay.class));
    }

    @Test
    void addDayRejectsCheckInStartNotBeforeStart() {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, organizer.getId(), EventRole.ORGANIZER)).thenReturn(true);

        CreateEventDayRequest request = new CreateEventDayRequest();
        request.setStartTime(LocalDateTime.of(2026, 6, 1, 9, 0));
        request.setEndTime(LocalDateTime.of(2026, 6, 1, 17, 0));
        request.setCheckInStartTime(LocalDateTime.of(2026, 6, 1, 9, 0)); // == start, invalid

        assertThatThrownBy(() -> dayService.addDay(eventId, request, organizer))
                .isInstanceOf(InvalidCheckInStartTimeException.class);
    }

    @Test
    void addDayThrowsWhenEventMissing() {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, organizer.getId(), EventRole.ORGANIZER)).thenReturn(true);
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        CreateEventDayRequest request = new CreateEventDayRequest();
        request.setStartTime(LocalDateTime.of(2026, 6, 1, 9, 0));
        request.setEndTime(LocalDateTime.of(2026, 6, 1, 17, 0));

        assertThatThrownBy(() -> dayService.addDay(eventId, request, organizer))
                .isInstanceOf(EventNotFoundException.class);
    }

    // ─── updateDay ──────────────────────────────────────────────────────────────

    @Test
    void updateDayAppliesProvidedFieldsAndLeavesNullsUntouched() {
        EventDay existing = EventDay.builder()
                .id(UUID.randomUUID())
                .event(event)
                .dayNumber(1)
                .title("Old title")
                .startTime(LocalDateTime.of(2026, 6, 1, 9, 0))
                .endTime(LocalDateTime.of(2026, 6, 1, 17, 0))
                .build();
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, organizer.getId(), EventRole.ORGANIZER)).thenReturn(true);
        when(dayRepository.findByIdAndEventId(existing.getId(), eventId))
                .thenReturn(Optional.of(existing));
        when(dayRepository.saveAndFlush(any(EventDay.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpdateEventDayRequest request = new UpdateEventDayRequest();
        request.setTitle("New title");
        // start/end/venue stay null

        EventsNestResponse<EventDayResponse> result =
                dayService.updateDay(eventId, existing.getId(), request, organizer);

        assertThat(result.getData().getTitle()).isEqualTo("New title");
        assertThat(result.getData().getStartTime()).isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 0));
        assertThat(result.getData().getEndTime()).isEqualTo(LocalDateTime.of(2026, 6, 1, 17, 0));
    }

    @Test
    void updateDayThrowsWhenDayMissing() {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, organizer.getId(), EventRole.ORGANIZER)).thenReturn(true);
        UUID dayId = UUID.randomUUID();
        when(dayRepository.findByIdAndEventId(dayId, eventId)).thenReturn(Optional.empty());

        UpdateEventDayRequest request = new UpdateEventDayRequest();
        request.setTitle("anything");

        assertThatThrownBy(() -> dayService.updateDay(eventId, dayId, request, organizer))
                .isInstanceOf(EventDayNotFoundException.class);
    }

    // ─── deleteDay ──────────────────────────────────────────────────────────────

    @Test
    void deleteDayRemovesTheDay() {
        EventDay existing = EventDay.builder()
                .id(UUID.randomUUID())
                .event(event)
                .dayNumber(2)
                .startTime(LocalDateTime.of(2026, 6, 2, 9, 0))
                .endTime(LocalDateTime.of(2026, 6, 2, 17, 0))
                .build();
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, organizer.getId(), EventRole.ORGANIZER)).thenReturn(true);
        when(dayRepository.findByIdAndEventId(existing.getId(), eventId))
                .thenReturn(Optional.of(existing));
        when(dayRepository.countByEventId(eventId)).thenReturn(3L);

        dayService.deleteDay(eventId, existing.getId(), organizer);

        verify(dayRepository).delete(existing);
    }

    @Test
    void deleteDayRefusesWhenItIsTheLastDay() {
        EventDay existing = EventDay.builder()
                .id(UUID.randomUUID())
                .event(event)
                .dayNumber(1)
                .startTime(LocalDateTime.of(2026, 6, 1, 9, 0))
                .endTime(LocalDateTime.of(2026, 6, 1, 17, 0))
                .build();
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, organizer.getId(), EventRole.ORGANIZER)).thenReturn(true);
        when(dayRepository.findByIdAndEventId(existing.getId(), eventId))
                .thenReturn(Optional.of(existing));
        when(dayRepository.countByEventId(eventId)).thenReturn(1L);

        assertThatThrownBy(() -> dayService.deleteDay(eventId, existing.getId(), organizer))
                .isInstanceOf(CannotDeleteLastEventDayException.class);
        verify(dayRepository, never()).delete(any(EventDay.class));
    }
}
