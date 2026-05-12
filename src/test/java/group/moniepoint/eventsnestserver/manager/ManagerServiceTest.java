package group.moniepoint.eventsnestserver.manager;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.manager.dto.request.AssignManagerRequest;
import group.moniepoint.eventsnestserver.manager.dto.response.ManagerResponse;
import group.moniepoint.eventsnestserver.manager.service.ManagerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ManagerServiceImpl.
 *
 * All collaborators are mocked. ModelMapper is used in real form to
 * faithfully test the toEventResponse mapping path without starting Spring.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ManagerService Unit Tests")
class ManagerServiceTest {

    @Mock private EventMembershipRepository membershipRepository;
    @Mock private EventRespository eventRepository;
    @Mock private UserRepository userRepository;

    private ManagerServiceImpl managerService;

    private final UUID eventId = UUID.randomUUID();
    private User organizer;
    private User candidate;
    private Events event;

    @BeforeEach
    void setUp() {
        managerService = new ManagerServiceImpl(
                membershipRepository, eventRepository, userRepository, new ModelMapper());

        organizer = User.builder().id("organizer001").firstName("Eve").lastName("Org")
                .email("eve@test.com").role(Role.USER).build();
        candidate = User.builder().id("candidate001").firstName("Mike").lastName("Mgr")
                .email("mike@test.com").role(Role.USER).build();
        event = Events.builder().id(eventId).title("Tech Conf").build();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private void stubOrganizer() {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(true);
    }

    private void stubNotOrganizer() {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(false);
    }

    private EventMembership membershipFor(User user) {
        return EventMembership.builder()
                .id(UUID.randomUUID())
                .user(user)
                .events(event)
                .role(EventRole.MANAGER)
                .assignedBy(organizer)
                .build();
    }

    // ─── assignManager() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("assignManager()")
    class AssignManager {

        @Test
        @DisplayName("Organizer can assign a new manager to their event")
        void organizerCanAssignManager() {
            stubOrganizer();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(userRepository.findByEmail(candidate.getEmail())).thenReturn(Optional.of(candidate));
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, candidate.getId(), EventRole.MANAGER))
                    .thenReturn(false);
            EventMembership saved = membershipFor(candidate);
            when(membershipRepository.save(any())).thenReturn(saved);

            AssignManagerRequest req = new AssignManagerRequest();
            req.setEmail(candidate.getEmail());

            EventsNestResponse<ManagerResponse> response =
                    managerService.assignManager(eventId, req, organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Manager assigned successfully");
            assertThat(response.getData().getUserId()).isEqualTo(candidate.getId());
            verify(membershipRepository).save(any(EventMembership.class));
        }

        @Test
        @DisplayName("Returns existing assignment message when user is already a manager")
        void idempotentWhenAlreadyManager() {
            stubOrganizer();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(userRepository.findByEmail(candidate.getEmail())).thenReturn(Optional.of(candidate));
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, candidate.getId(), EventRole.MANAGER))
                    .thenReturn(true);
            when(membershipRepository.findByEventsIdAndUserIdAndRole(eventId, candidate.getId(), EventRole.MANAGER))
                    .thenReturn(Optional.of(membershipFor(candidate)));

            AssignManagerRequest req = new AssignManagerRequest();
            req.setEmail(candidate.getEmail());

            EventsNestResponse<ManagerResponse> response =
                    managerService.assignManager(eventId, req, organizer);

            assertThat(response.getMessage()).isEqualTo("User is already a manager for this event");
            verify(membershipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when caller is not the organizer")
        void throwsWhenNotOrganizer() {
            stubNotOrganizer();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            AssignManagerRequest req = new AssignManagerRequest();
            req.setEmail(candidate.getEmail());

            assertThatThrownBy(() -> managerService.assignManager(eventId, req, organizer))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Throws IllegalArgumentException when organizer tries to assign themselves")
        void throwsWhenOrganizerAssignsSelf() {
            stubOrganizer();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(userRepository.findByEmail(organizer.getEmail())).thenReturn(Optional.of(organizer));
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.MANAGER))
                    .thenReturn(false);

            AssignManagerRequest req = new AssignManagerRequest();
            req.setEmail(organizer.getEmail());

            assertThatThrownBy(() -> managerService.assignManager(eventId, req, organizer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("themselves");
        }

        @Test
        @DisplayName("Throws EventNotFoundException for a non-existent event")
        void throwsWhenEventNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            AssignManagerRequest req = new AssignManagerRequest();
            req.setEmail(candidate.getEmail());

            assertThatThrownBy(() -> managerService.assignManager(eventId, req, organizer))
                    .isInstanceOf(EventNotFoundException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException for an unknown email address")
        void throwsWhenUserEmailNotFound() {
            stubOrganizer();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

            AssignManagerRequest req = new AssignManagerRequest();
            req.setEmail("unknown@test.com");

            assertThatThrownBy(() -> managerService.assignManager(eventId, req, organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── removeManager() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeManager()")
    class RemoveManager {

        @Test
        @DisplayName("Organizer can remove an existing manager")
        void organizerCanRemoveManager() {
            stubOrganizer();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, candidate.getId(), EventRole.MANAGER))
                    .thenReturn(true);

            EventsNestResponse<Void> response =
                    managerService.removeManager(eventId, candidate.getId(), organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Manager removed successfully");
            verify(membershipRepository).deleteByEventsIdAndUserIdAndRole(eventId, candidate.getId(), EventRole.MANAGER);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when manager assignment does not exist")
        void throwsWhenManagerNotFound() {
            stubOrganizer();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, candidate.getId(), EventRole.MANAGER))
                    .thenReturn(false);

            assertThatThrownBy(() -> managerService.removeManager(eventId, candidate.getId(), organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer attempts removal")
        void throwsWhenNotOrganizer() {
            stubNotOrganizer();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> managerService.removeManager(eventId, candidate.getId(), organizer))
                    .isInstanceOf(NotEventOrganizerException.class);
        }
    }

    // ─── getManagersForEvent() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getManagersForEvent()")
    class GetManagersForEvent {

        @Test
        @DisplayName("Organizer can list all managers for their event")
        void organizerCanListManagers() {
            stubOrganizer();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(membershipRepository.findAllByEventsIdAndRole(eventId, EventRole.MANAGER))
                    .thenReturn(List.of(membershipFor(candidate)));

            List<ManagerResponse> managers = managerService.getManagersForEvent(eventId, organizer);

            assertThat(managers).hasSize(1);
            assertThat(managers.get(0).getUserId()).isEqualTo(candidate.getId());
        }

        @Test
        @DisplayName("Returns empty list when event has no managers")
        void returnsEmptyWhenNoManagers() {
            stubOrganizer();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(membershipRepository.findAllByEventsIdAndRole(eventId, EventRole.MANAGER))
                    .thenReturn(List.of());

            List<ManagerResponse> managers = managerService.getManagersForEvent(eventId, organizer);

            assertThat(managers).isEmpty();
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer requests the list")
        void throwsWhenNotOrganizer() {
            stubNotOrganizer();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> managerService.getManagersForEvent(eventId, organizer))
                    .isInstanceOf(NotEventOrganizerException.class);
        }
    }

    // ─── getManagedEvents() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getManagedEvents()")
    class GetManagedEvents {

        @Test
        @DisplayName("Returns all events where the caller is a manager")
        void returnsAllManagedEvents() {
            EventMembership m = EventMembership.builder()
                    .id(UUID.randomUUID()).user(candidate).events(event).role(EventRole.MANAGER).build();
            when(membershipRepository.findAllByUserIdAndRole(candidate.getId(), EventRole.MANAGER))
                    .thenReturn(List.of(m));

            var events = managerService.getManagedEvents(candidate);

            assertThat(events).hasSize(1);
        }

        @Test
        @DisplayName("Returns empty list when user manages no events")
        void returnsEmptyForNonManager() {
            when(membershipRepository.findAllByUserIdAndRole(candidate.getId(), EventRole.MANAGER))
                    .thenReturn(List.of());

            var events = managerService.getManagedEvents(candidate);

            assertThat(events).isEmpty();
        }
    }
}
