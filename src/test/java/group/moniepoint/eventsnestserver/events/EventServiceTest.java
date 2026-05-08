package group.moniepoint.eventsnestserver.events;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventSummaryResponse;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.models.MembershipStatus;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.events.service.EventServiceImpl;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRespository eventRepository;

    @Mock
    private EventMembershipRepository membershipRepository;

    @Mock
    private TicketTierRepository tierRepository;

    private EventServiceImpl eventService;

    private User creator;

    @BeforeEach
    void setUp() {
        eventService = new EventServiceImpl(new ModelMapper(), eventRepository, membershipRepository, tierRepository);

        creator = User.builder()
                .id("testuser0001")
                .email("organizer@example.com")
                .firstName("Test")
                .lastName("Organizer")
                .passwordHash("hashed")
                .role(Role.USER)
                .build();
    }

    // ─── createEvent ────────────────────────────────────────────────────────────

    @Test
    void createEventPersistsMappedEntityWithDraftStatusAndReturnsResponse() {
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("Annual Hackathon");
        request.setVenue("Lagos Tech Hub");
        request.setStartTime(LocalDateTime.of(2026, 8, 1, 9, 0));
        request.setEndTime(LocalDateTime.of(2026, 8, 1, 18, 0));

        UUID generatedId = UUID.randomUUID();
        when(eventRepository.save(any(Events.class))).thenAnswer(inv -> {
            Events e = inv.getArgument(0);
            e.setId(generatedId);
            return e;
        });

        EventsNestResponse<EventResponse> response = eventService.createEvent(request, creator);

        ArgumentCaptor<Events> captor = ArgumentCaptor.forClass(Events.class);
        verify(eventRepository).save(captor.capture());
        Events saved = captor.getValue();

        assertThat(saved.getTitle()).isEqualTo("Annual Hackathon");
        assertThat(saved.getVenue()).isEqualTo("Lagos Tech Hub");
        assertThat(saved.getStartTime()).isEqualTo(request.getStartTime());
        assertThat(saved.getEndTime()).isEqualTo(request.getEndTime());
        assertThat(saved.getStatus()).isEqualTo(EventStatus.DRAFT);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Event created successfully");
        assertThat(response.getData().getId()).isEqualTo(generatedId);
        assertThat(response.getData().getTitle()).isEqualTo("Annual Hackathon");
        assertThat(response.getData().getVenue()).isEqualTo("Lagos Tech Hub");
        assertThat(response.getData().getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void createEventInsertsOrganizerMembershipForCreatingUser() {
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("Tech Summit");
        request.setVenue("Abuja Convention Centre");
        request.setStartTime(LocalDateTime.of(2026, 9, 1, 9, 0));
        request.setEndTime(LocalDateTime.of(2026, 9, 1, 17, 0));

        UUID eventId = UUID.randomUUID();
        when(eventRepository.save(any(Events.class))).thenAnswer(inv -> {
            Events e = inv.getArgument(0);
            e.setId(eventId);
            return e;
        });

        eventService.createEvent(request, creator);

        ArgumentCaptor<EventMembership> membershipCaptor = ArgumentCaptor.forClass(EventMembership.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        EventMembership membership = membershipCaptor.getValue();

        assertThat(membership.getUser()).isEqualTo(creator);
        assertThat(membership.getEvents().getId()).isEqualTo(eventId);
        assertThat(membership.getRole()).isEqualTo(EventRole.ORGANIZER);
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void createEventDoesNotSaveToEitherRepoWhenEventPersistenceFails() {
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("Failing Event");
        request.setVenue("Lagos Tech Hub");
        request.setStartTime(LocalDateTime.of(2026, 8, 1, 9, 0));
        request.setEndTime(LocalDateTime.of(2026, 8, 1, 18, 0));

        when(eventRepository.save(any(Events.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        assertThatThrownBy(() -> eventService.createEvent(request, creator))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB connection lost");

        verify(eventRepository).save(any(Events.class));
        verify(membershipRepository, never()).save(any(EventMembership.class));
    }

    // ─── getPublishedEvents ──────────────────────────────────────────────────────

    @Test
    void getPublishedEventsReturnsMappedSummariesForPublishedEvents() {
        Events e1 = publishedEvent(UUID.randomUUID(), "Lagos Startup Expo", "Eko Hotel");
        Events e2 = publishedEvent(UUID.randomUUID(), "DevFest Lagos", "UNILAG Auditorium");
        when(eventRepository.findAllByStatus(EventStatus.PUBLISHED)).thenReturn(List.of(e1, e2));

        List<EventSummaryResponse> result = eventService.getPublishedEvents();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(EventSummaryResponse::getTitle)
                .containsExactly("Lagos Startup Expo", "DevFest Lagos");
        assertThat(result).extracting(EventSummaryResponse::getStatus)
                .containsOnly(EventStatus.PUBLISHED);
    }

    @Test
    void getPublishedEventsReturnsEmptyListWhenNoneExist() {
        when(eventRepository.findAllByStatus(EventStatus.PUBLISHED)).thenReturn(List.of());

        assertThat(eventService.getPublishedEvents()).isEmpty();
    }

    // ─── getEventById ────────────────────────────────────────────────────────────

    @Test
    void getEventByIdReturnsMappedResponseWhenFound() {
        UUID id = UUID.randomUUID();
        Events event = draftEvent(id, "Fintech Forum", "Victoria Island");
        when(eventRepository.findById(id)).thenReturn(Optional.of(event));

        EventResponse response = eventService.getEventById(id);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getTitle()).isEqualTo("Fintech Forum");
        assertThat(response.getVenue()).isEqualTo("Victoria Island");
    }

    @Test
    void getEventByIdThrowsResourceNotFoundWhenEventDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEventById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("event not found");
    }

    // ─── updateEvent ─────────────────────────────────────────────────────────────

    @Test
    void updateEventAppliesOnlyProvidedFields() {
        UUID id = UUID.randomUUID();
        Events event = draftEvent(id, "Old Title", "Old Venue");
        when(eventRepository.findById(id)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(id, creator.getId(), EventRole.ORGANIZER))
                .thenReturn(true);
        when(eventRepository.save(any(Events.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateEventRequest request = new UpdateEventRequest();
        request.setTitle("New Title");

        EventsNestResponse<EventResponse> response = eventService.updateEvent(id, request, creator);

        ArgumentCaptor<Events> captor = ArgumentCaptor.forClass(Events.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("New Title");
        assertThat(captor.getValue().getVenue()).isEqualTo("Old Venue");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Event updated successfully");
    }

    @Test
    void updateEventThrowsUnauthorizedWhenUserIsNotOrganizer() {
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.of(draftEvent(id, "Event", "Venue")));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(id, creator.getId(), EventRole.ORGANIZER))
                .thenReturn(false);

        assertThatThrownBy(() -> eventService.updateEvent(id, new UpdateEventRequest(), creator))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("only the event organizer can perform this action");

        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateEventThrowsInvalidStateWhenEventIsPublished() {
        UUID id = UUID.randomUUID();
        Events event = publishedEvent(id, "Published Event", "Venue");
        when(eventRepository.findById(id)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(id, creator.getId(), EventRole.ORGANIZER))
                .thenReturn(true);

        assertThatThrownBy(() -> eventService.updateEvent(id, new UpdateEventRequest(), creator))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessage("cannot update a published event");

        verify(eventRepository, never()).save(any());
    }

    // ─── submitForApproval ───────────────────────────────────────────────────────

    @Test
    void submitForApprovalTransitionsDraftToPendingApproval() {
        UUID id = UUID.randomUUID();
        Events event = draftEvent(id, "Draft Event", "Venue");
        when(eventRepository.findById(id)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(id, creator.getId(), EventRole.ORGANIZER))
                .thenReturn(true);
        when(eventRepository.save(any(Events.class))).thenAnswer(inv -> inv.getArgument(0));

        EventsNestResponse<EventResponse> response = eventService.submitForApproval(id, creator);

        ArgumentCaptor<Events> captor = ArgumentCaptor.forClass(Events.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EventStatus.PENDING_APPROVAL);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Event submitted for approval");
        assertThat(response.getData().getStatus()).isEqualTo(EventStatus.PENDING_APPROVAL);
    }

    @Test
    void submitForApprovalThrowsUnauthorizedWhenUserIsNotOrganizer() {
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.of(draftEvent(id, "Event", "Venue")));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(id, creator.getId(), EventRole.ORGANIZER))
                .thenReturn(false);

        assertThatThrownBy(() -> eventService.submitForApproval(id, creator))
                .isInstanceOf(UnauthorizedException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void submitForApprovalThrowsInvalidStateWhenEventIsNotDraft() {
        UUID id = UUID.randomUUID();
        Events event = eventWithStatus(id, "Event", "Venue", EventStatus.PENDING_APPROVAL);
        when(eventRepository.findById(id)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(id, creator.getId(), EventRole.ORGANIZER))
                .thenReturn(true);

        assertThatThrownBy(() -> eventService.submitForApproval(id, creator))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessage("only DRAFT events can be submitted for approval");
    }

    // ─── deleteEvent ─────────────────────────────────────────────────────────────

    @Test
    void deleteEventSucceedsWhenEventIsDraft() {
        UUID id = UUID.randomUUID();
        Events event = draftEvent(id, "Event", "Venue");
        when(eventRepository.findById(id)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(id, creator.getId(), EventRole.ORGANIZER))
                .thenReturn(true);

        eventService.deleteEvent(id, creator);

        verify(eventRepository).delete(event);
    }

    @Test
    void deleteEventSucceedsWhenEventIsCancelled() {
        UUID id = UUID.randomUUID();
        Events event = eventWithStatus(id, "Event", "Venue", EventStatus.CANCELLED);
        when(eventRepository.findById(id)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(id, creator.getId(), EventRole.ORGANIZER))
                .thenReturn(true);

        eventService.deleteEvent(id, creator);

        verify(eventRepository).delete(event);
    }

    @Test
    void deleteEventThrowsUnauthorizedWhenUserIsNotOrganizer() {
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.of(draftEvent(id, "Event", "Venue")));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(id, creator.getId(), EventRole.ORGANIZER))
                .thenReturn(false);

        assertThatThrownBy(() -> eventService.deleteEvent(id, creator))
                .isInstanceOf(UnauthorizedException.class);

        verify(eventRepository, never()).delete(any());
    }

    @Test
    void deleteEventThrowsInvalidStateWhenEventIsPublished() {
        UUID id = UUID.randomUUID();
        Events event = publishedEvent(id, "Event", "Venue");
        when(eventRepository.findById(id)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(id, creator.getId(), EventRole.ORGANIZER))
                .thenReturn(true);

        assertThatThrownBy(() -> eventService.deleteEvent(id, creator))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessage("only DRAFT or CANCELLED events can be deleted");

        verify(eventRepository, never()).delete(any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private Events draftEvent(UUID id, String title, String venue) {
        return eventWithStatus(id, title, venue, EventStatus.DRAFT);
    }

    private Events publishedEvent(UUID id, String title, String venue) {
        return eventWithStatus(id, title, venue, EventStatus.PUBLISHED);
    }

    private Events eventWithStatus(UUID id, String title, String venue, EventStatus status) {
        Events event = new Events();
        event.setId(id);
        event.setTitle(title);
        event.setVenue(venue);
        event.setStartTime(LocalDateTime.of(2026, 10, 1, 9, 0));
        event.setEndTime(LocalDateTime.of(2026, 10, 1, 17, 0));
        event.setStatus(status);
        return event;
    }
}
