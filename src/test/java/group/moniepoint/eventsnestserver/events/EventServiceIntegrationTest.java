package group.moniepoint.eventsnestserver.events;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventSummaryResponse;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.EventVisibility;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.events.service.EventService;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.event.EventImageRequiredException;
import group.moniepoint.eventsnestserver.exception.event.EventNotDeletableException;
import group.moniepoint.eventsnestserver.exception.event.EventNotPublishedException;
import group.moniepoint.eventsnestserver.exception.event.EventNotSubmittableException;
import group.moniepoint.eventsnestserver.tiers.dto.request.CreateTicketTierRequest;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for EventService.
 *
 * Uses H2 in-memory database (JPA create-drop).
 * Redis and WebSocket dependencies are mocked by IntegrationTestConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Event Service Integration Tests")
@Import(IntegrationTestConfig.class)
class EventServiceIntegrationTest {

    @Autowired private EventService eventService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private TicketTierRepository tierRepository;

    private User organizer;
    private User stranger;

    @BeforeEach
    void seed() {
        organizer = userRepository.save(User.builder()
                .id("organizer001")
                .firstName("Eve")
                .lastName("Organizer")
                .email("eve@test.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .build());

        stranger = userRepository.save(User.builder()
                .id("stranger0001")
                .firstName("Sam")
                .lastName("Stranger")
                .email("sam@test.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .build());
    }

    // ─── createEvent() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createEvent()")
    class CreateEvent {

        @Test
        @DisplayName("Persists event in DRAFT status and returns populated response")
        void createsEventInDraftStatus() {
            EventsNestResponse<EventResponse> response =
                    eventService.createEvent(baseRequest("Tech Summit 2025"), organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Event created successfully");
            assertThat(response.getData().getId()).isNotNull();
            assertThat(response.getData().getTitle()).isEqualTo("Tech Summit 2025");
            assertThat(response.getData().getStatus()).isEqualTo(EventStatus.DRAFT);
        }

        @Test
        @DisplayName("Assigns ORGANIZER membership to the creator")
        void assignsOrganizerMembership() {
            EventResponse event = eventService.createEvent(baseRequest("Tech Summit 2025"), organizer).getData();

            boolean isOrg = membershipRepository
                    .existsByEventsIdAndUserIdAndRole(
                            event.getId(), organizer.getId(),
                            group.moniepoint.eventsnestserver.events.models.EventRole.ORGANIZER);
            assertThat(isOrg).isTrue();
        }

        @Test
        @DisplayName("Creates ticket tiers with correct capacity")
        void createsTicketTiersWithCorrectCapacity() {
            EventResponse event = eventService.createEvent(baseRequest("Tech Summit 2025"), organizer).getData();

            assertThat(event.getTiers()).hasSize(1);
            assertThat(event.getTiers().get(0).getTotalCapacity()).isEqualTo(100); // 10 rows × 10 seats
            assertThat(event.getTiers().get(0).getAvailableCapacity()).isEqualTo(100);
        }

        @Test
        @DisplayName("Generates a short unique event code")
        void generatesEventCode() {
            EventResponse event = eventService.createEvent(baseRequest("Tech Summit 2025"), organizer).getData();

            assertThat(event.getCode()).isNotBlank();
        }

        @Test
        @DisplayName("Defaults visibility to PUBLIC when not specified")
        void defaultsVisibilityToPublic() {
            EventResponse event = eventService.createEvent(baseRequest("Tech Summit 2025"), organizer).getData();

            assertThat(event.getVisibility()).isEqualTo(EventVisibility.PUBLIC);
        }

        @Test
        @DisplayName("Sets check-in start time 2 hours before event when not provided")
        void defaultsCheckInStartTime() {
            CreateEventRequest req = baseRequest("Tech Summit 2025");
            LocalDateTime start = req.getStartTime();

            EventResponse event = eventService.createEvent(req, organizer).getData();

            assertThat(event.getCheckInStartTime()).isEqualTo(start.minusHours(2));
        }
    }

    // ─── getPublishedEvents() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getPublishedEvents()")
    class GetPublishedEvents {

        @Test
        @DisplayName("Returns only PUBLISHED PUBLIC events")
        void returnsPublishedPublicEvents() {
            // Create and manually publish one event
            EventResponse event = eventService.createEvent(baseRequest("Public Event"), organizer).getData();
            eventRepository.findById(event.getId()).ifPresent(e -> {
                e.setStatus(EventStatus.PUBLISHED);
                e.setCoverImageUrl("https://img.test/cover.jpg");
                eventRepository.save(e);
            });

            // Create a DRAFT event — should be excluded
            eventService.createEvent(baseRequest("Draft Event"), organizer);

            List<EventSummaryResponse> published = eventService.getPublishedEvents();

            assertThat(published).hasSize(1);
            assertThat(published.get(0).getTitle()).isEqualTo("Public Event");
        }

        @Test
        @DisplayName("Returns empty list when no published events exist")
        void returnsEmptyWhenNonePublished() {
            assertThat(eventService.getPublishedEvents()).isEmpty();
        }

        @Test
        @DisplayName("Does not include PRIVATE published events")
        void excludesPrivatePublishedEvents() {
            EventResponse event = eventService.createEvent(baseRequest("Secret Event"), organizer).getData();
            eventRepository.findById(event.getId()).ifPresent(e -> {
                e.setStatus(EventStatus.PUBLISHED);
                e.setVisibility(EventVisibility.PRIVATE);
                e.setCoverImageUrl("https://img.test/cover.jpg");
                eventRepository.save(e);
            });

            assertThat(eventService.getPublishedEvents()).isEmpty();
        }
    }

    // ─── updateEvent() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateEvent()")
    class UpdateEvent {

        @Test
        @DisplayName("Organizer can update a DRAFT event freely")
        void organizerCanUpdateDraftEvent() {
            EventResponse event = eventService.createEvent(baseRequest("Old Title"), organizer).getData();

            UpdateEventRequest update = new UpdateEventRequest();
            update.setTitle("New Title");
            update.setVenue("New Venue");

            EventsNestResponse<EventResponse> response =
                    eventService.updateEvent(event.getId(), update, organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getTitle()).isEqualTo("New Title");
            assertThat(response.getData().getVenue()).isEqualTo("New Venue");
        }

        @Test
        @DisplayName("Non-organizer cannot update the event")
        void nonOrganizerCannotUpdate() {
            EventResponse event = eventService.createEvent(baseRequest("Event"), organizer).getData();

            UpdateEventRequest update = new UpdateEventRequest();
            update.setTitle("Hijacked Title");

            assertThatThrownBy(() -> eventService.updateEvent(event.getId(), update, stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException for a non-existent event")
        void throwsForNonExistentEvent() {
            assertThatThrownBy(() -> eventService.updateEvent(UUID.randomUUID(), new UpdateEventRequest(), organizer))
                    .isInstanceOf(group.moniepoint.eventsnestserver.exception.event.EventNotFoundException.class);
        }
    }

    // ─── submitForApproval() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("submitForApproval()")
    class SubmitForApproval {

        @Test
        @DisplayName("Organizer can submit a DRAFT event with a cover image for approval")
        void organizerCanSubmitDraftEvent() {
            EventResponse event = eventService.createEvent(baseRequest("Event"), organizer).getData();
            // Set cover image directly so submit passes validation
            eventRepository.findById(event.getId()).ifPresent(e -> {
                e.setCoverImageUrl("https://img.test/cover.jpg");
                eventRepository.save(e);
            });

            EventsNestResponse<EventResponse> response =
                    eventService.submitForApproval(event.getId(), organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getStatus()).isEqualTo(EventStatus.PENDING_APPROVAL);
        }

        @Test
        @DisplayName("Throws EventImageRequiredException when no cover image is set")
        void throwsWhenNoCoverImage() {
            EventResponse event = eventService.createEvent(baseRequest("Event"), organizer).getData();

            assertThatThrownBy(() -> eventService.submitForApproval(event.getId(), organizer))
                    .isInstanceOf(EventImageRequiredException.class);
        }

        @Test
        @DisplayName("Throws EventNotSubmittableException when event is not in DRAFT status")
        void throwsWhenEventIsNotDraft() {
            EventResponse event = eventService.createEvent(baseRequest("Event"), organizer).getData();
            eventRepository.findById(event.getId()).ifPresent(e -> {
                e.setCoverImageUrl("https://img.test/cover.jpg");
                e.setStatus(EventStatus.PUBLISHED);
                eventRepository.save(e);
            });

            assertThatThrownBy(() -> eventService.submitForApproval(event.getId(), organizer))
                    .isInstanceOf(EventNotSubmittableException.class);
        }

        @Test
        @DisplayName("Non-organizer cannot submit the event for approval")
        void nonOrganizerCannotSubmit() {
            EventResponse event = eventService.createEvent(baseRequest("Event"), organizer).getData();
            eventRepository.findById(event.getId()).ifPresent(e -> {
                e.setCoverImageUrl("https://img.test/cover.jpg");
                eventRepository.save(e);
            });

            assertThatThrownBy(() -> eventService.submitForApproval(event.getId(), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }
    }

    // ─── getMyEvents() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyEvents()")
    class GetMyEvents {

        @Test
        @DisplayName("Returns all events the caller created")
        void returnsOwnEvents() {
            eventService.createEvent(baseRequest("Event A"), organizer);
            eventService.createEvent(baseRequest("Event B"), organizer);

            List<EventResponse> mine = eventService.getMyEvents(organizer);

            assertThat(mine).hasSize(2);
            assertThat(mine).extracting(EventResponse::getTitle)
                    .containsExactlyInAnyOrder("Event A", "Event B");
        }

        @Test
        @DisplayName("Does not return events created by other users")
        void excludesOtherUsersEvents() {
            eventService.createEvent(baseRequest("Stranger's Event"), stranger);

            List<EventResponse> mine = eventService.getMyEvents(organizer);

            assertThat(mine).isEmpty();
        }

        @Test
        @DisplayName("Returns empty list when organizer has no events")
        void returnsEmptyForNewOrganizer() {
            assertThat(eventService.getMyEvents(organizer)).isEmpty();
        }
    }

    // ─── deleteEvent() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteEvent()")
    class DeleteEvent {

        @Test
        @DisplayName("Organizer can delete a DRAFT event")
        void organizerCanDeleteDraftEvent() {
            EventResponse event = eventService.createEvent(baseRequest("Event"), organizer).getData();

            eventService.deleteEvent(event.getId(), organizer);

            assertThat(eventRepository.findById(event.getId())).isEmpty();
        }

        @Test
        @DisplayName("Throws EventNotDeletableException when event is PUBLISHED")
        void throwsWhenEventIsPublished() {
            EventResponse event = eventService.createEvent(baseRequest("Event"), organizer).getData();
            eventRepository.findById(event.getId()).ifPresent(e -> {
                e.setStatus(EventStatus.PUBLISHED);
                eventRepository.save(e);
            });

            assertThatThrownBy(() -> eventService.deleteEvent(event.getId(), organizer))
                    .isInstanceOf(EventNotDeletableException.class);
        }

        @Test
        @DisplayName("Non-organizer cannot delete the event")
        void nonOrganizerCannotDelete() {
            EventResponse event = eventService.createEvent(baseRequest("Event"), organizer).getData();

            assertThatThrownBy(() -> eventService.deleteEvent(event.getId(), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }
    }

    // ─── getEventById() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getEventById()")
    class GetEventById {

        @Test
        @DisplayName("Returns event details for a PUBLISHED PUBLIC event")
        void returnsPublishedPublicEvent() {
            EventResponse event = eventService.createEvent(baseRequest("Public Event"), organizer).getData();
            eventRepository.findById(event.getId()).ifPresent(e -> {
                e.setStatus(EventStatus.PUBLISHED);
                e.setCoverImageUrl("https://img.test/cover.jpg");
                eventRepository.save(e);
            });

            EventResponse result = eventService.getEventById(event.getId(), null);

            assertThat(result.getTitle()).isEqualTo("Public Event");
        }

        @Test
        @DisplayName("Throws EventNotPublishedException for a DRAFT event")
        void throwsForDraftEvent() {
            EventResponse event = eventService.createEvent(baseRequest("Draft Event"), organizer).getData();

            assertThatThrownBy(() -> eventService.getEventById(event.getId(), null))
                    .isInstanceOf(EventNotPublishedException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException for a non-existent event id")
        void throwsForNonExistentId() {
            assertThatThrownBy(() -> eventService.getEventById(UUID.randomUUID(), null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CreateEventRequest baseRequest(String title) {
        CreateTicketTierRequest tier = new CreateTicketTierRequest();
        tier.setName("General");
        tier.setPrice(new BigDecimal("5000.00"));
        tier.setRowPrefix("A");
        tier.setRowCount(10);
        tier.setSeatsPerRow(10);

        CreateEventRequest req = new CreateEventRequest();
        req.setTitle(title);
        req.setDescription("Integration test event");
        req.setVenue("Test Arena, Lagos");
        req.setStartTime(LocalDateTime.now().plusDays(30));
        req.setEndTime(LocalDateTime.now().plusDays(30).plusHours(6));
        req.setTiers(List.of(tier));
        return req;
    }
}
