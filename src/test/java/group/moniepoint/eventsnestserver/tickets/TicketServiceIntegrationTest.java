package group.moniepoint.eventsnestserver.tickets;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.bookings.dto.response.BookingResponse;
import group.moniepoint.eventsnestserver.bookings.kafka.BookingEventPublisher;
import group.moniepoint.eventsnestserver.bookings.models.BookingStatus;
import group.moniepoint.eventsnestserver.bookings.models.PaymentStatus;
import group.moniepoint.eventsnestserver.bookings.service.BookingService;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.models.EventVisibility;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.bookings.dto.request.CreateBookingRequest;
import group.moniepoint.eventsnestserver.bookings.repository.BookingRepository;
import group.moniepoint.eventsnestserver.tickets.dto.response.TicketResponse;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import group.moniepoint.eventsnestserver.tickets.service.TicketService;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ticket issuance.
 *
 * Tests the BookingService → TicketService flow:
 *  1. createBooking() — confirms immediately, issues tickets, creates membership
 *  2. getMyTickets()  — lists issued tickets for an attendee
 *
 * BookingEventPublisher is mocked (Kafka not available in CI).
 * Redis and WebSocket dependencies are mocked by IntegrationTestConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Ticket & Payment Integration Tests")
@Import(IntegrationTestConfig.class)
class TicketServiceIntegrationTest {

    @Autowired private BookingService bookingService;
    @Autowired private TicketService ticketService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private TicketTierRepository tierRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private TicketRepository ticketRepository;

    @MockitoBean private BookingEventPublisher bookingEventPublisher;

    private User organizer;
    private User attendee;
    private Events event;
    private TicketTier tier;

    @BeforeEach
    void seed() {
        organizer = userRepository.save(User.builder()
                .id("organizer001")
                .firstName("Eve").lastName("Organizer")
                .email("eve@test.com").passwordHash("hashed").role(Role.USER)
                .build());

        attendee = userRepository.save(User.builder()
                .id("attendee0001")
                .firstName("Alice").lastName("Attendee")
                .email("alice@test.com").passwordHash("hashed").role(Role.USER)
                .build());

        event = eventRepository.save(Events.builder()
                .title("Tech Summit 2025")
                .description("Annual tech conference")
                .venue("Lagos Convention Centre")
                .startTime(LocalDateTime.now().plusDays(30))
                .endTime(LocalDateTime.now().plusDays(30).plusHours(8))
                .status(EventStatus.PUBLISHED)
                .visibility(EventVisibility.PUBLIC)
                .createdBy(organizer)
                .build());

        membershipRepository.save(EventMembership.builder()
                .events(event).user(organizer).role(EventRole.ORGANIZER).assignedBy(organizer)
                .status(group.moniepoint.eventsnestserver.events.models.MembershipStatus.ACTIVE)
                .build());

        tier = tierRepository.save(TicketTier.builder()
                .event(event).name("General Admission")
                .price(new BigDecimal("5000.00"))
                .rowPrefix("A").rowCount(10).seatsPerRow(10)
                .totalCapacity(100).availableCapacity(100)
                .build());
    }

    // ─── createBooking() — immediate confirmation ─────────────────────────────────

    @Nested
    @DisplayName("createBooking() — immediate ticket issuance")
    class CreateBookingIssuesTickets {

        @Test
        @DisplayName("Issues tickets immediately on booking creation")
        void issuesTicketsOnBookingCreation() {
            EventsNestResponse<BookingResponse> response =
                    bookingService.createBooking(event.getId(), bookingRequest(3), attendee);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("tickets issued");
            assertThat(response.getData().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(response.getData().getStatus()).isEqualTo(BookingStatus.CONFIRMED);

            List<TicketResponse> tickets = ticketService.getMyTickets(attendee);
            assertThat(tickets).hasSize(3);
            assertThat(tickets).allMatch(t -> t.getStatus() == TicketStatus.VALID);
        }

        @Test
        @DisplayName("Seat labels follow the row/position scheme defined in PRD §3.4")
        void seatLabelsFollowRowPositionScheme() {
            bookingService.createBooking(event.getId(), bookingRequest(3), attendee);

            List<TicketResponse> tickets = ticketService.getMyTickets(attendee);
            // Tier: rowPrefix=A, seatsPerRow=10. Tickets 0,1,2 → A1-1, A1-2, A1-3
            assertThat(tickets).extracting(TicketResponse::getSeatNumber)
                    .containsExactlyInAnyOrder("A1-1", "A1-2", "A1-3");
        }

        @Test
        @DisplayName("Second booking for the same attendee accumulates tickets correctly")
        void secondBookingAccumulatesTickets() {
            bookingService.createBooking(event.getId(), bookingRequest(2), attendee);
            bookingService.createBooking(event.getId(), bookingRequest(1), attendee);

            assertThat(ticketService.getMyTickets(attendee)).hasSize(3);
        }
    }

    // ─── getMyTickets() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyTickets()")
    class GetMyTickets {

        @Test
        @DisplayName("Returns all valid tickets owned by the attendee")
        void returnsAllOwnedTickets() {
            bookingService.createBooking(event.getId(), bookingRequest(2), attendee);

            List<TicketResponse> tickets = ticketService.getMyTickets(attendee);

            assertThat(tickets).hasSize(2);
            assertThat(tickets).allMatch(t -> t.getEventId().equals(event.getId()));
        }

        @Test
        @DisplayName("Returns empty list when attendee has no tickets")
        void returnsEmptyWhenNoTickets() {
            List<TicketResponse> tickets = ticketService.getMyTickets(attendee);

            assertThat(tickets).isEmpty();
        }

        @Test
        @DisplayName("Ticket response includes event title, venue, and seat number")
        void ticketIncludesEventDetails() {
            bookingService.createBooking(event.getId(), bookingRequest(1), attendee);

            TicketResponse ticket = ticketService.getMyTickets(attendee).get(0);

            assertThat(ticket.getEventTitle()).isEqualTo("Tech Summit 2025");
            assertThat(ticket.getEventVenue()).isEqualTo("Lagos Convention Centre");
            assertThat(ticket.getSeatNumber()).isNotBlank();
            assertThat(ticket.getQrCode()).isNotBlank();
            assertThat(ticket.getShortCode()).isNotBlank();
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private CreateBookingRequest bookingRequest(int quantity) {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setTierId(tier.getId());
        req.setQuantity(quantity);
        return req;
    }
}
