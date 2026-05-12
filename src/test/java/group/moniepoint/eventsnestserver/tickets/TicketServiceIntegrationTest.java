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
import group.moniepoint.eventsnestserver.payments.MonnifyClient;
import group.moniepoint.eventsnestserver.payments.MonnifyPaymentStatus;
import group.moniepoint.eventsnestserver.payments.dto.InitializeTransactionResponse;
import group.moniepoint.eventsnestserver.payments.dto.VerifyTransactionResponse;
import group.moniepoint.eventsnestserver.tickets.dto.response.TicketResponse;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import group.moniepoint.eventsnestserver.tickets.service.TicketService;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for ticket issuance and payment finalization.
 *
 * Tests the full BookingService → TicketService flow:
 *  1. createBooking()           — reserve seats, initiate Monnify payment
 *  2. finalizeBookingPayment()  — verify with Monnify, issue tickets
 *  3. markBookingFailed()       — mark payment failed, restore capacity
 *  4. getMyTickets()            — list issued tickets for an attendee
 *
 * MonnifyClient and BookingEventPublisher are mocked — external services.
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

    @MockitoBean private MonnifyClient monnifyClient;
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

        AtomicInteger counter = new AtomicInteger(1);
        when(monnifyClient.initializeTransaction(any())).thenAnswer(inv -> {
            String ref = "MONNIFY-REF-" + counter.getAndIncrement();
            return InitializeTransactionResponse.builder()
                    .transactionReference(ref)
                    .paymentReference("booking-ref")
                    .checkoutUrl("https://pay.monnify.com/checkout/" + ref)
                    .build();
        });
    }

    // ─── finalizeBookingPayment() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("finalizeBookingPayment()")
    class FinalizeBookingPayment {

        @Test
        @DisplayName("Issues tickets and marks booking PAID when Monnify confirms payment")
        void issuesTicketsOnPaymentConfirmation() {
            BookingResponse booking = bookingService
                    .createBooking(event.getId(), bookingRequest(3), attendee).getData();

            when(monnifyClient.verifyTransaction(anyString()))
                    .thenReturn(VerifyTransactionResponse.builder()
                            .status(MonnifyPaymentStatus.PAID)
                            .build());

            EventsNestResponse<BookingResponse> response =
                    bookingService.finalizeBookingPayment(booking.getTransactionReference());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("tickets issued");
            assertThat(response.getData().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

            // 3 tickets should be issued
            List<TicketResponse> tickets = ticketService.getMyTickets(attendee);
            assertThat(tickets).hasSize(3);
            assertThat(tickets).allMatch(t -> t.getStatus() == TicketStatus.VALID);
        }

        @Test
        @DisplayName("Seat labels follow the row/position scheme defined in PRD §3.4")
        void seatLabelsFollowRowPositionScheme() {
            BookingResponse booking = bookingService
                    .createBooking(event.getId(), bookingRequest(3), attendee).getData();

            when(monnifyClient.verifyTransaction(anyString()))
                    .thenReturn(VerifyTransactionResponse.builder()
                            .status(MonnifyPaymentStatus.PAID).build());

            bookingService.finalizeBookingPayment(booking.getTransactionReference());

            List<TicketResponse> tickets = ticketService.getMyTickets(attendee);
            // Tier: rowPrefix=A, seatsPerRow=10. Tickets 0,1,2 → A1-1, A1-2, A1-3
            assertThat(tickets).extracting(TicketResponse::getSeatNumber)
                    .containsExactlyInAnyOrder("A1-1", "A1-2", "A1-3");
        }

        @Test
        @DisplayName("Is idempotent — repeated call returns already-paid response without duplicate tickets")
        void idempotentOnDoubleFinalization() {
            BookingResponse booking = bookingService
                    .createBooking(event.getId(), bookingRequest(2), attendee).getData();

            when(monnifyClient.verifyTransaction(anyString()))
                    .thenReturn(VerifyTransactionResponse.builder()
                            .status(MonnifyPaymentStatus.PAID).build());

            bookingService.finalizeBookingPayment(booking.getTransactionReference());
            EventsNestResponse<BookingResponse> repeat =
                    bookingService.finalizeBookingPayment(booking.getTransactionReference());

            assertThat(repeat.getMessage()).isEqualTo("Booking already paid");
            assertThat(ticketService.getMyTickets(attendee)).hasSize(2);
        }

        @Test
        @DisplayName("Does not issue tickets when Monnify reports payment not yet confirmed")
        void doesNotIssueTicketsWhenPaymentPending() {
            BookingResponse booking = bookingService
                    .createBooking(event.getId(), bookingRequest(1), attendee).getData();

            when(monnifyClient.verifyTransaction(anyString()))
                    .thenReturn(VerifyTransactionResponse.builder()
                            .status(MonnifyPaymentStatus.PENDING).build());

            EventsNestResponse<BookingResponse> response =
                    bookingService.finalizeBookingPayment(booking.getTransactionReference());

            assertThat(response.isSuccess()).isFalse();
            assertThat(ticketService.getMyTickets(attendee)).isEmpty();
        }
    }

    // ─── markBookingFailed() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("markBookingFailed()")
    class MarkBookingFailed {

        @Test
        @DisplayName("Marks booking as FAILED and restores tier capacity")
        void marksFailedAndRestoresCapacity() {
            bookingService.createBooking(event.getId(), bookingRequest(5), attendee);
            BookingResponse booking = bookingService
                    .createBooking(event.getId(), bookingRequest(3), attendee).getData();

            bookingService.markBookingFailed(booking.getTransactionReference(), "PAYMENT_EXPIRED");

            var updated = tierRepository.findById(tier.getId()).orElseThrow();
            // 5 still reserved; 3 restored → 95
            assertThat(updated.getAvailableCapacity()).isEqualTo(95);

            var b = bookingRepository.findById(booking.getId()).orElseThrow();
            assertThat(b.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        }

        @Test
        @DisplayName("Is idempotent — repeated call on already-failed booking is a no-op")
        void idempotentOnAlreadyFailed() {
            BookingResponse booking = bookingService
                    .createBooking(event.getId(), bookingRequest(2), attendee).getData();

            bookingService.markBookingFailed(booking.getTransactionReference(), "PAYMENT_CANCELLED");
            EventsNestResponse<BookingResponse> repeat =
                    bookingService.markBookingFailed(booking.getTransactionReference(), "PAYMENT_CANCELLED");

            assertThat(repeat.isSuccess()).isTrue();
        }
    }

    // ─── getMyTickets() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyTickets()")
    class GetMyTickets {

        @Test
        @DisplayName("Returns all valid tickets owned by the attendee")
        void returnsAllOwnedTickets() {
            BookingResponse booking = bookingService
                    .createBooking(event.getId(), bookingRequest(2), attendee).getData();

            when(monnifyClient.verifyTransaction(anyString()))
                    .thenReturn(VerifyTransactionResponse.builder()
                            .status(MonnifyPaymentStatus.PAID).build());
            bookingService.finalizeBookingPayment(booking.getTransactionReference());

            List<TicketResponse> tickets = ticketService.getMyTickets(attendee);

            assertThat(tickets).hasSize(2);
            assertThat(tickets).allMatch(t -> t.getEventId().equals(event.getId()));
        }

        @Test
        @DisplayName("Returns empty list when attendee has no paid tickets")
        void returnsEmptyWhenNoTickets() {
            List<TicketResponse> tickets = ticketService.getMyTickets(attendee);

            assertThat(tickets).isEmpty();
        }

        @Test
        @DisplayName("Ticket response includes event title, venue, and seat number")
        void ticketIncludesEventDetails() {
            BookingResponse booking = bookingService
                    .createBooking(event.getId(), bookingRequest(1), attendee).getData();

            when(monnifyClient.verifyTransaction(anyString()))
                    .thenReturn(VerifyTransactionResponse.builder()
                            .status(MonnifyPaymentStatus.PAID).build());
            bookingService.finalizeBookingPayment(booking.getTransactionReference());

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
