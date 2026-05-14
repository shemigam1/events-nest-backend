package group.moniepoint.eventsnestserver.manager;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.manager.dto.request.AssignManagerRequest;
import group.moniepoint.eventsnestserver.manager.dto.response.ManagerResponse;
import group.moniepoint.eventsnestserver.manager.service.ManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for ManagerService.
 *
 * Uses H2 in-memory database (JPA create-drop).
 * Redis and WebSocket dependencies are mocked by IntegrationTestConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Manager Service Integration Tests")
@Import(IntegrationTestConfig.class)
class ManagerServiceIntegrationTest {

    @Autowired private ManagerService managerService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;

    private User organizer;
    private User candidate;
    private User stranger;
    private Events event;

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

        candidate = userRepository.save(User.builder()
                .id("candidate001")
                .firstName("Mike")
                .lastName("Manager")
                .email("mike@test.com")
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

        event = eventRepository.save(Events.builder()
                .title("Tech Summit 2025")
                .description("Annual tech conference")
                .venue("Lagos Convention Centre")
                .startTime(LocalDateTime.now().plusDays(30))
                .endTime(LocalDateTime.now().plusDays(30).plusHours(8))
                .status(EventStatus.PUBLISHED)
                .createdBy(organizer)
                .build());

        membershipRepository.save(EventMembership.builder()
                .events(event)
                .user(organizer)
                .role(EventRole.ORGANIZER)
                .assignedBy(organizer)
                .build());
    }

    // ─── assignManager() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("assignManager()")
    class AssignManager {

        @Test
        @DisplayName("Organizer can assign a manager and a MANAGER membership is created")
        void organizerCanAssignManager() {
            EventsNestResponse<ManagerResponse> response =
                    managerService.assignManager(event.getId(), assignRequest(candidate.getEmail()), organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Manager assigned successfully");
            assertThat(response.getData().getUserId()).isEqualTo(candidate.getId());
            assertThat(response.getData().getEmail()).isEqualTo("mike@test.com");

            assertThat(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    event.getId(), candidate.getId(), EventRole.MANAGER)).isTrue();
        }

        @Test
        @DisplayName("Assigning the same manager twice is idempotent")
        void assigningSameManagerTwiceIsIdempotent() {
            managerService.assignManager(event.getId(), assignRequest(candidate.getEmail()), organizer);
            EventsNestResponse<ManagerResponse> second =
                    managerService.assignManager(event.getId(), assignRequest(candidate.getEmail()), organizer);

            assertThat(second.isSuccess()).isTrue();
            long managerCount = membershipRepository
                    .findAllByEventsIdAndRole(event.getId(), EventRole.MANAGER).size();
            assertThat(managerCount).isEqualTo(1); // not doubled
        }

        @Test
        @DisplayName("Throws IllegalArgumentException when organizer assigns themselves as manager")
        void organizerCannotAssignSelf() {
            assertThatThrownBy(() ->
                    managerService.assignManager(event.getId(), assignRequest(organizer.getEmail()), organizer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("organizers cannot assign themselves");
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer tries to assign")
        void nonOrganizerCannotAssign() {
            assertThatThrownBy(() ->
                    managerService.assignManager(event.getId(), assignRequest(candidate.getEmail()), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when candidate email does not exist")
        void throwsForUnknownCandidateEmail() {
            assertThatThrownBy(() ->
                    managerService.assignManager(event.getId(), assignRequest("nobody@test.com"), organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws IllegalStateException when event is not published")
        void throwsWhenEventNotPublished() {
            Events draftEvent = eventRepository.save(Events.builder()
                    .title("Draft Event")
                    .venue("Some Venue")
                    .status(EventStatus.DRAFT)
                    .createdBy(organizer)
                    .startTime(LocalDateTime.now().plusDays(10))
                    .endTime(LocalDateTime.now().plusDays(11))
                    .build());

            membershipRepository.save(EventMembership.builder()
                    .events(draftEvent)
                    .user(organizer)
                    .role(EventRole.ORGANIZER)
                    .assignedBy(organizer)
                    .build());

            assertThatThrownBy(() ->
                    managerService.assignManager(draftEvent.getId(), assignRequest(candidate.getEmail()), organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("published events");
        }
    }

    // ─── removeManager() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeManager()")
    class RemoveManager {

        @Test
        @DisplayName("Organizer can remove a manager")
        void organizerCanRemoveManager() {
            managerService.assignManager(event.getId(), assignRequest(candidate.getEmail()), organizer);

            EventsNestResponse<Void> response =
                    managerService.removeManager(event.getId(), candidate.getId(), organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    event.getId(), candidate.getId(), EventRole.MANAGER)).isFalse();
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when manager id is not assigned to event")
        void throwsWhenManagerNotFound() {
            assertThatThrownBy(() ->
                    managerService.removeManager(event.getId(), candidate.getId(), organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer tries to remove manager")
        void nonOrganizerCannotRemove() {
            managerService.assignManager(event.getId(), assignRequest(candidate.getEmail()), organizer);

            assertThatThrownBy(() ->
                    managerService.removeManager(event.getId(), candidate.getId(), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Throws IllegalStateException when event is not published")
        void throwsWhenEventNotPublished() {
            Events pendingEvent = eventRepository.save(Events.builder()
                    .title("Pending Event")
                    .venue("Some Venue")
                    .status(EventStatus.PENDING_APPROVAL)
                    .createdBy(organizer)
                    .startTime(LocalDateTime.now().plusDays(10))
                    .endTime(LocalDateTime.now().plusDays(11))
                    .build());

            membershipRepository.save(EventMembership.builder()
                    .events(pendingEvent)
                    .user(organizer)
                    .role(EventRole.ORGANIZER)
                    .assignedBy(organizer)
                    .build());

            assertThatThrownBy(() ->
                    managerService.removeManager(pendingEvent.getId(), candidate.getId(), organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("published events");
        }
    }

    // ─── getManagersForEvent() ────────────────────────────────────────────────

    @Nested
    @DisplayName("getManagersForEvent()")
    class GetManagersForEvent {

        @Test
        @DisplayName("Organizer can list all managers for their event")
        void organizerCanListManagers() {
            managerService.assignManager(event.getId(), assignRequest(candidate.getEmail()), organizer);

            List<ManagerResponse> managers = managerService.getManagersForEvent(event.getId(), organizer);

            assertThat(managers).hasSize(1);
            assertThat(managers.get(0).getEmail()).isEqualTo("mike@test.com");
        }

        @Test
        @DisplayName("Returns empty list when no managers are assigned")
        void returnsEmptyWhenNoManagers() {
            assertThat(managerService.getManagersForEvent(event.getId(), organizer)).isEmpty();
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer requests the list")
        void nonOrganizerCannotList() {
            assertThatThrownBy(() -> managerService.getManagersForEvent(event.getId(), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Throws IllegalStateException when event is not published")
        void throwsWhenEventNotPublished() {
            Events draftEvent = eventRepository.save(Events.builder()
                    .title("Draft Event")
                    .venue("Some Venue")
                    .status(EventStatus.DRAFT)
                    .createdBy(organizer)
                    .startTime(LocalDateTime.now().plusDays(10))
                    .endTime(LocalDateTime.now().plusDays(11))
                    .build());

            membershipRepository.save(EventMembership.builder()
                    .events(draftEvent)
                    .user(organizer)
                    .role(EventRole.ORGANIZER)
                    .assignedBy(organizer)
                    .build());

            assertThatThrownBy(() -> managerService.getManagersForEvent(draftEvent.getId(), organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("published events");
        }
    }

    // ─── getManagedEvents() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getManagedEvents()")
    class GetManagedEvents {

        @Test
        @DisplayName("Returns all events the caller is a manager of")
        void returnsManagedEvents() {
            managerService.assignManager(event.getId(), assignRequest(candidate.getEmail()), organizer);

            List<?> events = managerService.getManagedEvents(candidate);

            assertThat(events).hasSize(1);
        }

        @Test
        @DisplayName("Returns empty list when caller manages no events")
        void returnsEmptyForNonManager() {
            assertThat(managerService.getManagedEvents(stranger)).isEmpty();
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private AssignManagerRequest assignRequest(String email) {
        AssignManagerRequest req = new AssignManagerRequest();
        req.setEmail(email);
        return req;
    }
}
