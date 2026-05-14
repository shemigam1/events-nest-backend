package group.moniepoint.eventsnestserver.bookings;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.bookings.dto.request.CreateBookingRequest;
import group.moniepoint.eventsnestserver.bookings.dto.response.BookingResponse;
import group.moniepoint.eventsnestserver.bookings.kafka.BookingEventPublisher;
import group.moniepoint.eventsnestserver.bookings.models.BookingStatus;
import group.moniepoint.eventsnestserver.bookings.models.PaymentStatus;
import group.moniepoint.eventsnestserver.bookings.repository.BookingRepository;
import group.moniepoint.eventsnestserver.bookings.service.BookingService;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.exception.booking.BookingCancellationForbiddenException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.exception.ticket.InsufficientTierCapacityException;
import group.moniepoint.eventsnestserver.exception.ticket.TicketTierNotFoundException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for BookingService.
 *
 * Uses H2 in-memory database (JPA create-drop).
 * BookingEventPublisher is mocked — it calls Kafka which is unavailable in CI.
 * Redis and WebSocket dependencies are mocked by IntegrationTestConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Booking Service Integration Tests")
@Import(IntegrationTestConfig.class)
class BookingServiceIntegrationTest {

    @Autowired private BookingService bookingService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private TicketTierRepository tierRepository;
    @Autowired private BookingRepository bookingRepository;

    @MockitoBean private BookingEventPublisher bookingEventPublisher;

    private User organizer;
    private User attendee;
    private Events event;
    private TicketTier tier;

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

        attendee = userRepository.save(User.builder()
                .id("attendee0001")
                .firstName("Alice")
                .lastName("Attendee")
                .email("alice@test.com")
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
                .visibility(group.moniepoint.eventsnestserver.events.models.EventVisibility.PUBLIC)
                .createdBy(organizer)
                .build());

        membershipRepository.save(EventMembership.builder()
                .events(event)
                .user(organizer)
                .role(EventRole.ORGANIZER)
                .status(group.moniepoint.eventsnestserver.events.models.MembershipStatus.ACTIVE)
                .assignedBy(organizer)
                .build());

        tier = tierRepository.save(TicketTier.builder()
                .event(event)
                .name("General Admission")
                .price(new BigDecimal("5000.00"))
                .rowPrefix("A")
                .rowCount(10)
                .seatsPerRow(10)
                .totalCapacity(100)
                .availableCapacity(100)
                .build());

    }

    // ─── createBooking() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("createBooking()")
    class CreateBooking {

        @Test
        @DisplayName("Creates a CONFIRMED booking with tickets issued immediately")
        void createsConfirmedBookingWithTickets() {
            EventsNestResponse<BookingResponse> response =
                    bookingService.createBooking(event.getId(), bookingRequest(tier.getId(), 2), attendee);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("tickets issued");
            assertThat(response.getData().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(response.getData().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(response.getData().getTickets()).hasSize(2);
        }

        @Test
        @DisplayName("Calculates total amount as tier price × quantity")
        void calculatesTotalAmount() {
            EventsNestResponse<BookingResponse> response =
                    bookingService.createBooking(event.getId(), bookingRequest(tier.getId(), 3), attendee);

            assertThat(response.getData().getTotalAmount())
                    .isEqualByComparingTo(new BigDecimal("15000.00")); // 5000 × 3
        }

        @Test
        @DisplayName("Reduces available capacity by the booked quantity")
        void reducesAvailableCapacity() {
            bookingService.createBooking(event.getId(), bookingRequest(tier.getId(), 5), attendee);

            TicketTier updated = tierRepository.findById(tier.getId()).orElseThrow();
            assertThat(updated.getAvailableCapacity()).isEqualTo(95); // 100 − 5
        }

        @Test
        @DisplayName("Throws InvalidEventStateException when event is not PUBLISHED")
        void throwsForUnpublishedEvent() {
            event.setStatus(EventStatus.DRAFT);
            eventRepository.save(event);

            assertThatThrownBy(() ->
                    bookingService.createBooking(event.getId(), bookingRequest(tier.getId(), 1), attendee))
                    .isInstanceOf(InvalidEventStateException.class);
        }

        @Test
        @DisplayName("Throws UnauthorizedException when organizer tries to book own event")
        void throwsWhenOrganizerBooksOwnEvent() {
            assertThatThrownBy(() ->
                    bookingService.createBooking(event.getId(), bookingRequest(tier.getId(), 1), organizer))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("organizers cannot book");
        }

        @Test
        @DisplayName("Throws EventNotFoundException for a non-existent event")
        void throwsForNonExistentEvent() {
            assertThatThrownBy(() ->
                    bookingService.createBooking(UUID.randomUUID(), bookingRequest(tier.getId(), 1), attendee))
                    .isInstanceOf(EventNotFoundException.class);
        }

        @Test
        @DisplayName("Throws TicketTierNotFoundException for a non-existent tier")
        void throwsForNonExistentTier() {
            assertThatThrownBy(() ->
                    bookingService.createBooking(event.getId(), bookingRequest(UUID.randomUUID(), 1), attendee))
                    .isInstanceOf(TicketTierNotFoundException.class);
        }

        @Test
        @DisplayName("Throws InsufficientTierCapacityException when quantity exceeds available seats")
        void throwsWhenCapacityExceeded() {
            assertThatThrownBy(() ->
                    bookingService.createBooking(event.getId(), bookingRequest(tier.getId(), 101), attendee))
                    .isInstanceOf(InsufficientTierCapacityException.class);
        }
    }

    // ─── getMyBookings() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyBookings()")
    class GetMyBookings {

        @Test
        @DisplayName("Returns all bookings made by the caller")
        void returnsOwnBookings() {
            bookingService.createBooking(event.getId(), bookingRequest(tier.getId(), 1), attendee);
            bookingService.createBooking(event.getId(), bookingRequest(tier.getId(), 2), attendee);

            List<BookingResponse> mine = bookingService.getMyBookings(attendee);

            assertThat(mine).hasSize(2);
        }

        @Test
        @DisplayName("Does not return bookings made by other users")
        void excludesOtherUsersBookings() {
            User other = userRepository.save(User.builder()
                    .id("other0000001")
                    .firstName("Bob").lastName("Other")
                    .email("bob@test.com")
                    .passwordHash("hashed")
                    .role(Role.USER)
                    .build());
            bookingService.createBooking(event.getId(), bookingRequest(tier.getId(), 1), other);

            assertThat(bookingService.getMyBookings(attendee)).isEmpty();
        }

        @Test
        @DisplayName("Returns empty list when caller has no bookings")
        void returnsEmptyForNewUser() {
            assertThat(bookingService.getMyBookings(attendee)).isEmpty();
        }
    }

    // ─── cancelBooking() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelBooking()")
    class CancelBooking {

        @Test
        @DisplayName("Attendee can cancel their own booking")
        void attendeeCanCancelBooking() {
            BookingResponse booking =
                    bookingService.createBooking(event.getId(), bookingRequest(tier.getId(), 2), attendee).getData();

            EventsNestResponse<BookingResponse> response =
                    bookingService.cancelBooking(event.getId(), booking.getId(), attendee);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        }

        @Test
        @DisplayName("Cancellation restores the reserved capacity")
        void cancelRestoresCapacity() {
            BookingResponse booking =
                    bookingService.createBooking(event.getId(), bookingRequest(tier.getId(), 3), attendee).getData();

            bookingService.cancelBooking(event.getId(), booking.getId(), attendee);

            TicketTier updated = tierRepository.findById(tier.getId()).orElseThrow();
            assertThat(updated.getAvailableCapacity()).isEqualTo(100); // fully restored
        }

        @Test
        @DisplayName("Throws BookingCancellationForbiddenException when another user tries to cancel")
        void anotherUserCannotCancel() {
            User other = userRepository.save(User.builder()
                    .id("other0000001")
                    .firstName("Bob").lastName("Other")
                    .email("bob@test.com")
                    .passwordHash("hashed")
                    .role(Role.USER)
                    .build());
            BookingResponse booking =
                    bookingService.createBooking(event.getId(), bookingRequest(tier.getId(), 1), attendee).getData();

            assertThatThrownBy(() -> bookingService.cancelBooking(event.getId(), booking.getId(), other))
                    .isInstanceOf(BookingCancellationForbiddenException.class);
        }
    }

    // ─── getBookingsForEventByOrganiser() ────────────────────────────────────

    @Nested
    @DisplayName("getBookingsForEventByOrganiser()")
    class GetBookingsForEventByOrganiser {

        @Test
        @DisplayName("Organizer can retrieve all bookings for their event")
        void organizerCanViewAllBookings() {
            bookingService.createBooking(event.getId(), bookingRequest(tier.getId(), 1), attendee);

            List<BookingResponse> bookings =
                    bookingService.getBookingsForEventByOrganiser(event.getId(), organizer);

            assertThat(bookings).hasSize(1);
        }

        @Test
        @DisplayName("Throws UnauthorizedException when non-organizer requests the list")
        void nonOrganizerCannotViewBookings() {
            assertThatThrownBy(() ->
                    bookingService.getBookingsForEventByOrganiser(event.getId(), attendee))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Returns empty list when no bookings have been made yet")
        void returnsEmptyWhenNoBookings() {
            List<BookingResponse> bookings =
                    bookingService.getBookingsForEventByOrganiser(event.getId(), organizer);

            assertThat(bookings).isEmpty();
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CreateBookingRequest bookingRequest(UUID tierId, int quantity) {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setTierId(tierId);
        req.setQuantity(quantity);
        return req;
    }
}
