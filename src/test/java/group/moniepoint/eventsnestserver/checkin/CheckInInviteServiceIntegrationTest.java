package group.moniepoint.eventsnestserver.checkin;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.checkin.dto.request.CreateCheckInInviteRequest;
import group.moniepoint.eventsnestserver.checkin.dto.response.CheckInInviteResponse;
import group.moniepoint.eventsnestserver.checkin.model.CheckInInviteStatus;
import group.moniepoint.eventsnestserver.checkin.repository.CheckInInviteRepository;
import group.moniepoint.eventsnestserver.checkin.service.CheckInInviteService;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.email.EmailOutbox;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.checkin.CheckInInviteNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for CheckInInviteService.
 *
 * Uses H2 in-memory database (JPA create-drop).
 * EmailOutbox is mocked — it calls SMTP which is unavailable in test.
 * Redis and WebSocket dependencies are mocked by IntegrationTestConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("CheckInInviteService Integration Tests")
@Import(IntegrationTestConfig.class)
class CheckInInviteServiceIntegrationTest {

    @Autowired private CheckInInviteService checkInInviteService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private CheckInInviteRepository inviteRepository;

    @MockitoBean private EmailOutbox emailOutbox;

    private User organizer;
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

    // ─── createInvite() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createInvite()")
    class CreateInvite {

        @Test
        @DisplayName("Organizer can create a check-in staff invite")
        void organizerCanCreateInvite() {
            CreateCheckInInviteRequest req = inviteRequest("John Doe", "john@staff.com");

            EventsNestResponse<CheckInInviteResponse> response =
                    checkInInviteService.createInvite(event.getId(), req, organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("token");
            assertThat(response.getData().getName()).isEqualTo("John Doe");
            assertThat(response.getData().getStatus()).isEqualTo(CheckInInviteStatus.ACTIVE);
            // Raw token must be returned on creation only
            assertThat(response.getData().getRawToken()).isNotNull().startsWith("ckin_");
        }

        @Test
        @DisplayName("Token is hashed in the database — raw token is not stored")
        void tokenIsHashedInStorage() {
            CreateCheckInInviteRequest req = inviteRequest("Jane Doe", null);

            EventsNestResponse<CheckInInviteResponse> response =
                    checkInInviteService.createInvite(event.getId(), req, organizer);

            String rawToken = response.getData().getRawToken();
            // Find the stored invite and verify the hash does not equal the raw token
            var stored = inviteRepository.findById(response.getData().getId()).orElseThrow();
            assertThat(stored.getTokenHash()).isNotEqualTo(rawToken);
        }

        @Test
        @DisplayName("Invite expires 24 hours after the event ends")
        void inviteExpiresAfterEventEnd() {
            CreateCheckInInviteRequest req = inviteRequest("Staff", null);

            EventsNestResponse<CheckInInviteResponse> response =
                    checkInInviteService.createInvite(event.getId(), req, organizer);

            assertThat(response.getData().getExpiresAt())
                    .isAfterOrEqualTo(event.getEndTime().plusHours(23))
                    .isBeforeOrEqualTo(event.getEndTime().plusHours(25));
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when a non-organizer tries to create an invite")
        void throwsForNonOrganizer() {
            CreateCheckInInviteRequest req = inviteRequest("Intruder", null);

            assertThatThrownBy(() -> checkInInviteService.createInvite(event.getId(), req, stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }
    }

    // ─── listInvites() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listInvites()")
    class ListInvites {

        @Test
        @DisplayName("Organizer can list all invites for their event")
        void organizerCanListInvites() {
            checkInInviteService.createInvite(event.getId(), inviteRequest("Staff A", null), organizer);
            checkInInviteService.createInvite(event.getId(), inviteRequest("Staff B", null), organizer);

            List<CheckInInviteResponse> invites = checkInInviteService.listInvites(event.getId(), organizer);

            assertThat(invites).hasSize(2);
        }

        @Test
        @DisplayName("Returns empty list when no invites have been created")
        void returnsEmptyWhenNoInvites() {
            List<CheckInInviteResponse> invites = checkInInviteService.listInvites(event.getId(), organizer);

            assertThat(invites).isEmpty();
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer requests the list")
        void throwsForNonOrganizer() {
            assertThatThrownBy(() -> checkInInviteService.listInvites(event.getId(), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }
    }

    // ─── revokeInvite() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("revokeInvite()")
    class RevokeInvite {

        @Test
        @DisplayName("Organizer can revoke an active invite")
        void organizerCanRevokeInvite() {
            UUID inviteId = checkInInviteService
                    .createInvite(event.getId(), inviteRequest("Staff", null), organizer)
                    .getData().getId();

            EventsNestResponse<Void> response =
                    checkInInviteService.revokeInvite(event.getId(), inviteId, organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Check-in invite revoked");

            var stored = inviteRepository.findById(inviteId).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(CheckInInviteStatus.REVOKED);
        }

        @Test
        @DisplayName("Throws CheckInInviteNotFoundException for a non-existent invite")
        void throwsForNonExistentInvite() {
            assertThatThrownBy(() ->
                    checkInInviteService.revokeInvite(event.getId(), UUID.randomUUID(), organizer))
                    .isInstanceOf(CheckInInviteNotFoundException.class);
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer tries to revoke")
        void throwsForNonOrganizer() {
            UUID inviteId = checkInInviteService
                    .createInvite(event.getId(), inviteRequest("Staff", null), organizer)
                    .getData().getId();

            assertThatThrownBy(() ->
                    checkInInviteService.revokeInvite(event.getId(), inviteId, stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private CreateCheckInInviteRequest inviteRequest(String name, String email) {
        CreateCheckInInviteRequest req = new CreateCheckInInviteRequest();
        req.setName(name);
        req.setEmail(email);
        return req;
    }
}
