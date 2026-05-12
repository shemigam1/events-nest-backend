package group.moniepoint.eventsnestserver.guestlist;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.email.EmailOutbox;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.guestlist.DuplicateGuestEmailException;
import group.moniepoint.eventsnestserver.exception.guestlist.GuestListNotEnabledException;
import group.moniepoint.eventsnestserver.guestlist.dto.request.CreateGuestRequest;
import group.moniepoint.eventsnestserver.guestlist.dto.request.RsvpRequest;
import group.moniepoint.eventsnestserver.guestlist.dto.request.UpdateGuestStatusRequest;
import group.moniepoint.eventsnestserver.guestlist.dto.response.GuestResponse;
import group.moniepoint.eventsnestserver.guestlist.model.RsvpStatus;
import group.moniepoint.eventsnestserver.guestlist.repository.GuestRepository;
import group.moniepoint.eventsnestserver.guestlist.service.GuestService;
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
 * Integration tests for GuestService.
 *
 * Uses H2 in-memory database (JPA create-drop).
 * EmailOutbox is mocked — it sends real emails which are unavailable in tests.
 * Redis and WebSocket dependencies are mocked by IntegrationTestConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Guest Service Integration Tests")
@Import(IntegrationTestConfig.class)
class GuestServiceIntegrationTest {

    @Autowired private GuestService guestService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private EventConfigRepository configRepository;
    @Autowired private GuestRepository guestRepository;

    // EmailOutbox sends actual emails — mock it to avoid SMTP/third-party calls.
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
                .title("Exclusive Gala 2025")
                .description("Annual gala event")
                .venue("Eko Hotel, Lagos")
                .startTime(LocalDateTime.now().plusDays(30))
                .endTime(LocalDateTime.now().plusDays(30).plusHours(5))
                .status(EventStatus.PUBLISHED)
                .createdBy(organizer)
                .build());

        membershipRepository.save(EventMembership.builder()
                .events(event)
                .user(organizer)
                .role(EventRole.ORGANIZER)
                .assignedBy(organizer)
                .build());

        // Enable guest list in the event config
        configRepository.save(EventConfig.builder()
                .event(event)
                .guestListEnabled(true)
                .build());
    }

    // ─── addGuest() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addGuest()")
    class AddGuest {

        @Test
        @DisplayName("Organizer can add a guest and it is persisted with PENDING status")
        void organizerCanAddGuest() {
            EventsNestResponse<GuestResponse> response =
                    guestService.addGuest(event.getId(), guestRequest("John Doe", "john@test.com"), organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Guest added successfully");
            assertThat(response.getData().getName()).isEqualTo("John Doe");
            assertThat(response.getData().getEmail()).isEqualTo("john@test.com");
            assertThat(response.getData().getRsvpStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Throws DuplicateGuestEmailException when email already exists for the event")
        void throwsOnDuplicateEmail() {
            guestService.addGuest(event.getId(), guestRequest("John Doe", "john@test.com"), organizer);

            assertThatThrownBy(() ->
                    guestService.addGuest(event.getId(), guestRequest("Jane Doe", "john@test.com"), organizer))
                    .isInstanceOf(DuplicateGuestEmailException.class);
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer tries to add a guest")
        void throwsWhenNonOrganizerAddsGuest() {
            assertThatThrownBy(() ->
                    guestService.addGuest(event.getId(), guestRequest("John", "john@test.com"), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Throws GuestListNotEnabledException when guest list is disabled in config")
        void throwsWhenGuestListDisabled() {
            // Override config — disable guest list
            configRepository.findByEventId(event.getId()).ifPresent(c -> {
                c.setGuestListEnabled(false);
                configRepository.save(c);
            });

            assertThatThrownBy(() ->
                    guestService.addGuest(event.getId(), guestRequest("John", "john@test.com"), organizer))
                    .isInstanceOf(GuestListNotEnabledException.class);
        }
    }

    // ─── getGuests() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getGuests()")
    class GetGuests {

        @Test
        @DisplayName("Organizer can retrieve all guests for their event")
        void organizerCanListGuests() {
            guestService.addGuest(event.getId(), guestRequest("Alice", "alice@test.com"), organizer);
            guestService.addGuest(event.getId(), guestRequest("Bob", "bob@test.com"), organizer);

            List<GuestResponse> guests = guestService.getGuests(event.getId(), organizer);

            assertThat(guests).hasSize(2);
        }

        @Test
        @DisplayName("Returns empty list when no guests have been added")
        void returnsEmptyWhenNoGuests() {
            assertThat(guestService.getGuests(event.getId(), organizer)).isEmpty();
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer requests the list")
        void throwsForNonOrganizer() {
            assertThatThrownBy(() -> guestService.getGuests(event.getId(), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }
    }

    // ─── updateGuestStatus() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("updateGuestStatus()")
    class UpdateGuestStatus {

        @Test
        @DisplayName("Organizer can manually update guest RSVP status")
        void organizerCanUpdateStatus() {
            GuestResponse guest =
                    guestService.addGuest(event.getId(), guestRequest("Alice", "alice@test.com"), organizer)
                                .getData();

            UpdateGuestStatusRequest req = new UpdateGuestStatusRequest();
            req.setRsvpStatus(RsvpStatus.ACCEPTED);

            GuestResponse updated = guestService.updateGuestStatus(event.getId(), guest.getId(), req, organizer)
                                                .getData();

            assertThat(updated.getRsvpStatus()).isEqualTo("ACCEPTED");
        }
    }

    // ─── removeGuest() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeGuest()")
    class RemoveGuest {

        @Test
        @DisplayName("Organizer can remove a guest")
        void organizerCanRemoveGuest() {
            GuestResponse guest =
                    guestService.addGuest(event.getId(), guestRequest("Alice", "alice@test.com"), organizer)
                                .getData();

            guestService.removeGuest(event.getId(), guest.getId(), organizer);

            assertThat(guestRepository.findById(guest.getId())).isEmpty();
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer tries to remove")
        void throwsForNonOrganizer() {
            GuestResponse guest =
                    guestService.addGuest(event.getId(), guestRequest("Alice", "alice@test.com"), organizer)
                                .getData();

            assertThatThrownBy(() -> guestService.removeGuest(event.getId(), guest.getId(), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }
    }

    // ─── rsvp() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rsvp()")
    class Rsvp {

        @Test
        @DisplayName("Guest can accept an RSVP via their token")
        void guestCanAcceptRsvp() {
            // Add guest — in tests we retrieve the raw token via the hash stored in the DB.
            // We simulate the flow: add guest, then call rsvp with a valid token by using
            // the service's TokenHashUtils in reverse — not feasible. Instead we verify
            // that the RSVP endpoint accepts a well-known token stored during addGuest.
            // We do this by finding the guest and using the hash directly in the DB.
            guestService.addGuest(event.getId(), guestRequest("Alice", "alice@test.com"), organizer);

            // Retrieve the raw guest record to extract the rsvpTokenHash (hash of the real token).
            // We can't reverse SHA-256, so we set a known token on the record.
            var rawGuest = guestRepository.findAllByEventId(event.getId()).get(0);
            String knownToken = "test-token-12345";
            rawGuest.setRsvpTokenHash(group.moniepoint.eventsnestserver.util.TokenHashUtils.hash(knownToken));
            guestRepository.save(rawGuest);

            RsvpRequest req = new RsvpRequest();
            req.setToken(knownToken);
            req.setResponse("ACCEPTED");

            EventsNestResponse<GuestResponse> response = guestService.rsvp(req);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getRsvpStatus()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("Guest can decline an RSVP via their token")
        void guestCanDeclineRsvp() {
            guestService.addGuest(event.getId(), guestRequest("Bob", "bob@test.com"), organizer);

            var rawGuest = guestRepository.findAllByEventId(event.getId()).get(0);
            String knownToken = "decline-token-abc";
            rawGuest.setRsvpTokenHash(group.moniepoint.eventsnestserver.util.TokenHashUtils.hash(knownToken));
            guestRepository.save(rawGuest);

            RsvpRequest req = new RsvpRequest();
            req.setToken(knownToken);
            req.setResponse("DECLINED");

            EventsNestResponse<GuestResponse> response = guestService.rsvp(req);

            assertThat(response.getData().getRsvpStatus()).isEqualTo("DECLINED");
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CreateGuestRequest guestRequest(String name, String email) {
        CreateGuestRequest req = new CreateGuestRequest();
        req.setName(name);
        req.setEmail(email);
        req.setSendInvite(false); // avoids triggering emailOutbox in most tests
        return req;
    }
}
