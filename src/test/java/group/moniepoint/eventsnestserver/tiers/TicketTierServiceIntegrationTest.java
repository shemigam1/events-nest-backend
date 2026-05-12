package group.moniepoint.eventsnestserver.tiers;

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
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.exception.ticket.TicketTierNotFoundException;
import group.moniepoint.eventsnestserver.tiers.dto.request.CreateTicketTierRequest;
import group.moniepoint.eventsnestserver.tiers.dto.request.UpdateTicketTierRequest;
import group.moniepoint.eventsnestserver.tiers.dto.response.TicketTierResponse;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import group.moniepoint.eventsnestserver.tiers.service.TicketTierService;
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
 * Integration tests for TicketTierService.
 *
 * Uses H2 in-memory database (JPA create-drop).
 * Redis and WebSocket dependencies are mocked by IntegrationTestConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Ticket Tier Service Integration Tests")
@Import(IntegrationTestConfig.class)
class TicketTierServiceIntegrationTest {

    @Autowired private TicketTierService tierService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private TicketTierRepository tierRepository;

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
                .description("Annual tech event")
                .venue("Lagos Convention Centre")
                .startTime(LocalDateTime.now().plusDays(30))
                .endTime(LocalDateTime.now().plusDays(30).plusHours(8))
                .status(EventStatus.DRAFT)
                .createdBy(organizer)
                .build());

        membershipRepository.save(EventMembership.builder()
                .events(event)
                .user(organizer)
                .role(EventRole.ORGANIZER)
                .assignedBy(organizer)
                .build());
    }

    // ─── createTier() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createTier()")
    class CreateTier {

        @Test
        @DisplayName("Organizer can create a tier and its capacity is rowCount × seatsPerRow")
        void organizerCanCreateTier() {
            EventsNestResponse<TicketTierResponse> response =
                    tierService.createTier(event.getId(), tierRequest("VIP", "5000.00", "V", 5, 10), organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Ticket tier created successfully");
            assertThat(response.getData().getName()).isEqualTo("VIP");
            assertThat(response.getData().getTotalCapacity()).isEqualTo(50);      // 5 × 10
            assertThat(response.getData().getAvailableCapacity()).isEqualTo(50);
        }

        @Test
        @DisplayName("Non-organizer cannot create a tier")
        void nonOrganizerCannotCreateTier() {
            assertThatThrownBy(() ->
                    tierService.createTier(event.getId(), tierRequest("VIP", "5000.00", "V", 5, 10), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Throws EventNotFoundException for a non-existent event")
        void throwsForNonExistentEvent() {
            assertThatThrownBy(() ->
                    tierService.createTier(UUID.randomUUID(), tierRequest("VIP", "5000.00", "V", 5, 10), organizer))
                    .isInstanceOf(EventNotFoundException.class);
        }

        @Test
        @DisplayName("Tier is persisted and linked to the correct event")
        void tierIsLinkedToEvent() {
            TicketTierResponse response =
                    tierService.createTier(event.getId(), tierRequest("General", "2000.00", "G", 10, 20), organizer)
                               .getData();

            assertThat(response.getEventId()).isEqualTo(event.getId());
            assertThat(tierRepository.findAllByEventId(event.getId())).hasSize(1);
        }
    }

    // ─── getTiersByEvent() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTiersByEvent()")
    class GetTiersByEvent {

        @Test
        @DisplayName("Returns all tiers for a given event")
        void returnsAllTiersForEvent() {
            tierService.createTier(event.getId(), tierRequest("VIP", "10000.00", "V", 5, 5), organizer);
            tierService.createTier(event.getId(), tierRequest("General", "3000.00", "G", 10, 20), organizer);

            List<TicketTierResponse> tiers = tierService.getTiersByEvent(event.getId());

            assertThat(tiers).hasSize(2);
            assertThat(tiers).extracting(TicketTierResponse::getName)
                    .containsExactlyInAnyOrder("VIP", "General");
        }

        @Test
        @DisplayName("Returns empty list when event has no tiers")
        void returnsEmptyWhenNoTiers() {
            assertThat(tierService.getTiersByEvent(event.getId())).isEmpty();
        }

        @Test
        @DisplayName("Throws EventNotFoundException for a non-existent event")
        void throwsForNonExistentEvent() {
            assertThatThrownBy(() -> tierService.getTiersByEvent(UUID.randomUUID()))
                    .isInstanceOf(EventNotFoundException.class);
        }
    }

    // ─── updateTier() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateTier()")
    class UpdateTier {

        @Test
        @DisplayName("Organizer can update tier name and price")
        void organizerCanUpdateTier() {
            TicketTierResponse created =
                    tierService.createTier(event.getId(), tierRequest("VIP", "5000.00", "V", 5, 10), organizer)
                               .getData();

            UpdateTicketTierRequest update = new UpdateTicketTierRequest();
            update.setName("Premium VIP");
            update.setPrice(new BigDecimal("8000.00"));

            EventsNestResponse<TicketTierResponse> response =
                    tierService.updateTier(event.getId(), created.getId(), update, organizer);

            assertThat(response.getData().getName()).isEqualTo("Premium VIP");
            assertThat(response.getData().getPrice()).isEqualByComparingTo("8000.00");
        }

        @Test
        @DisplayName("Non-organizer cannot update a tier")
        void nonOrganizerCannotUpdateTier() {
            TicketTierResponse created =
                    tierService.createTier(event.getId(), tierRequest("VIP", "5000.00", "V", 5, 10), organizer)
                               .getData();

            assertThatThrownBy(() ->
                    tierService.updateTier(event.getId(), created.getId(), new UpdateTicketTierRequest(), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Throws TicketTierNotFoundException when tier does not exist")
        void throwsForNonExistentTier() {
            assertThatThrownBy(() ->
                    tierService.updateTier(event.getId(), UUID.randomUUID(), new UpdateTicketTierRequest(), organizer))
                    .isInstanceOf(TicketTierNotFoundException.class);
        }
    }

    // ─── deleteTier() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteTier()")
    class DeleteTier {

        @Test
        @DisplayName("Organizer can delete an unused tier")
        void organizerCanDeleteTier() {
            TicketTierResponse created =
                    tierService.createTier(event.getId(), tierRequest("VIP", "5000.00", "V", 5, 10), organizer)
                               .getData();

            tierService.deleteTier(event.getId(), created.getId(), organizer);

            assertThat(tierRepository.findById(created.getId())).isEmpty();
        }

        @Test
        @DisplayName("Non-organizer cannot delete a tier")
        void nonOrganizerCannotDeleteTier() {
            TicketTierResponse created =
                    tierService.createTier(event.getId(), tierRequest("VIP", "5000.00", "V", 5, 10), organizer)
                               .getData();

            assertThatThrownBy(() -> tierService.deleteTier(event.getId(), created.getId(), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Throws TicketTierNotFoundException when tier does not belong to the event")
        void throwsWhenTierBelongsToDifferentEvent() {
            Events otherEvent = eventRepository.save(Events.builder()
                    .title("Other Event")
                    .description("Other")
                    .venue("Other Venue")
                    .startTime(LocalDateTime.now().plusDays(60))
                    .endTime(LocalDateTime.now().plusDays(60).plusHours(4))
                    .status(EventStatus.DRAFT)
                    .createdBy(organizer)
                    .build());

            TicketTier otherTier = tierRepository.save(TicketTier.builder()
                    .event(otherEvent)
                    .name("General")
                    .price(new BigDecimal("1000.00"))
                    .rowPrefix("G")
                    .rowCount(5)
                    .seatsPerRow(5)
                    .totalCapacity(25)
                    .availableCapacity(25)
                    .build());

            assertThatThrownBy(() -> tierService.deleteTier(event.getId(), otherTier.getId(), organizer))
                    .isInstanceOf(TicketTierNotFoundException.class);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CreateTicketTierRequest tierRequest(String name, String price, String prefix,
                                                int rows, int seatsPerRow) {
        CreateTicketTierRequest req = new CreateTicketTierRequest();
        req.setName(name);
        req.setPrice(new BigDecimal(price));
        req.setRowPrefix(prefix);
        req.setRowCount(rows);
        req.setSeatsPerRow(seatsPerRow);
        return req;
    }
}
