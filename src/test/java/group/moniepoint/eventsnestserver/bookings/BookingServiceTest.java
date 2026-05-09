package group.moniepoint.eventsnestserver.bookings;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.bookings.dto.request.CreateBookingRequest;
import group.moniepoint.eventsnestserver.bookings.dto.response.BookingResponse;
import group.moniepoint.eventsnestserver.bookings.kafka.BookingEventPublisher;
import group.moniepoint.eventsnestserver.bookings.models.Booking;
import group.moniepoint.eventsnestserver.bookings.models.BookingStatus;
import group.moniepoint.eventsnestserver.bookings.models.PaymentStatus;
import group.moniepoint.eventsnestserver.bookings.repository.BookingRepository;
import group.moniepoint.eventsnestserver.bookings.service.BookingServiceImpl;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.tickets.dto.response.TicketResponse;
import group.moniepoint.eventsnestserver.tickets.models.Ticket;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import group.moniepoint.eventsnestserver.tickets.service.TicketService;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private TicketTierRepository tierRepository;
    @Mock private EventRespository eventRepository;
    @Mock private EventMembershipRepository membershipRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketService ticketService;
    @Mock private BookingEventPublisher eventPublisher;

    private BookingServiceImpl bookingService;

    private User attendee;
    private Events event;
    private TicketTier tier;
    private UUID eventId;
    private UUID tierId;

    @BeforeEach
    void setUp() {
        bookingService = new BookingServiceImpl(
                bookingRepository, tierRepository, eventRepository,
                membershipRepository, ticketRepository, ticketService, eventPublisher,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        attendee = User.builder()
                .id("attendee0001")
                .email("attendee@example.com")
                .firstName("Test")
                .lastName("Attendee")
                .passwordHash("hashed")
                .role(Role.USER)
                .build();

        eventId = UUID.randomUUID();
        event = new Events();
        event.setId(eventId);
        event.setTitle("Tech Summit");
        event.setStatus(EventStatus.PUBLISHED);

        tierId = UUID.randomUUID();
        tier = TicketTier.builder()
                .id(tierId)
                .event(event)
                .name("VIP")
                .price(new BigDecimal("100.00"))
                .rowPrefix("VIP")
                .rowCount(5)
                .seatsPerRow(10)
                .totalCapacity(50)
                .availableCapacity(50)
                .build();
    }

    // ─── createBooking ───────────────────────────────────────────────────────────

    @Test
    void createBookingDecrementsTierCapacityAndIssuesTickets() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
        // Production calls saveAndFlush so the generated ID is visible before
        // ticket issuance / Kafka publish (it's referenced in the event payload).
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
        when(ticketService.issueTickets(any(), any(), any(), anyInt())).thenReturn(List.of());
        // toTicketResponse only called when the ticket list is non-empty;
        // issueTickets returns empty here so this stub would be unnecessary.
        // Production now checks ORGANIZER first (to assert the booker isn't
        // the event's own organiser, which is forbidden) before the ATTENDEE
        // membership idempotency check.
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, attendee.getId(), EventRole.ORGANIZER)).thenReturn(false);
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, attendee.getId(), EventRole.ATTENDEE)).thenReturn(false);

        CreateBookingRequest request = bookingRequest(tierId, 3);
        EventsNestResponse<BookingResponse> response = bookingService.createBooking(eventId, request, attendee);

        ArgumentCaptor<TicketTier> tierCaptor = ArgumentCaptor.forClass(TicketTier.class);
        verify(tierRepository).save(tierCaptor.capture());
        assertThat(tierCaptor.getValue().getAvailableCapacity()).isEqualTo(47);

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).saveAndFlush(bookingCaptor.capture());
        Booking saved = bookingCaptor.getValue();
        assertThat(saved.getQuantity()).isEqualTo(3);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("300.00");
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        verify(ticketService).issueTickets(saved, tier, attendee, 3);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Booking confirmed");
    }

    @Test
    void createBookingInsertsAttendeeMembershipWhenNotAlreadyPresent() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
        // Production now checks ORGANIZER first (to assert the booker isn't
        // the event's own organiser, which is forbidden) before the ATTENDEE
        // membership idempotency check.
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, attendee.getId(), EventRole.ORGANIZER)).thenReturn(false);
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, attendee.getId(), EventRole.ATTENDEE)).thenReturn(false);

        bookingService.createBooking(eventId, bookingRequest(tierId, 1), attendee);

        ArgumentCaptor<EventMembership> captor = ArgumentCaptor.forClass(EventMembership.class);
        verify(membershipRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(attendee);
        assertThat(captor.getValue().getRole()).isEqualTo(EventRole.ATTENDEE);
    }

    @Test
    void createBookingDoesNotInsertAttendeeMembershipWhenAlreadyPresent() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, attendee.getId(), EventRole.ORGANIZER)).thenReturn(false);
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, attendee.getId(), EventRole.ATTENDEE)).thenReturn(true);

        bookingService.createBooking(eventId, bookingRequest(tierId, 1), attendee);

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void createBookingThrowsWhenEventIsNotPublished() {
        event.setStatus(EventStatus.DRAFT);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> bookingService.createBooking(eventId, bookingRequest(tierId, 1), attendee))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessage("only published events can be booked");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBookingThrowsWhenTierBelongsToDifferentEvent() {
        Events other = new Events();
        other.setId(UUID.randomUUID());
        tier.setEvent(other);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));

        assertThatThrownBy(() -> bookingService.createBooking(eventId, bookingRequest(tierId, 1), attendee))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("ticket tier not found");
    }

    @Test
    void createBookingThrowsWhenInsufficientCapacity() {
        tier.setAvailableCapacity(2);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));

        assertThatThrownBy(() -> bookingService.createBooking(eventId, bookingRequest(tierId, 5), attendee))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessage("insufficient capacity for this tier");

        verify(bookingRepository, never()).save(any());
    }

    // ─── cancelBooking ───────────────────────────────────────────────────────────

    @Test
    void cancelBookingRefundsTicketsRestoresCapacityAndRemovesMembership() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .attendee(attendee)
                .event(event)
                .tier(tier)
                .quantity(3)
                .status(BookingStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        tier.setAvailableCapacity(47); // 50 - 3 already decremented by booking

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
        when(ticketRepository.findAllByBookingId(bookingId)).thenReturn(List.of());

        bookingService.cancelBooking(eventId, bookingId, attendee);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);

        ArgumentCaptor<TicketTier> tierCaptor = ArgumentCaptor.forClass(TicketTier.class);
        verify(tierRepository).save(tierCaptor.capture());
        assertThat(tierCaptor.getValue().getAvailableCapacity()).isEqualTo(50);

        verify(ticketService).refundTicketsForBooking(bookingId);
        verify(membershipRepository).deleteByEventsIdAndUserIdAndRole(
                eventId, attendee.getId(), EventRole.ATTENDEE);
    }

    @Test
    void cancelBookingThrowsUnauthorizedWhenRequestingUserIsNotAttendee() {
        UUID bookingId = UUID.randomUUID();
        User otherUser = User.builder().id("otheruser0001").build();
        Booking booking = Booking.builder()
                .id(bookingId).attendee(otherUser).event(event).tier(tier)
                .status(BookingStatus.CONFIRMED).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(eventId, bookingId, attendee))
                .isInstanceOf(UnauthorizedException.class);

        verify(bookingRepository, never()).save(any());
        verify(ticketService, never()).refundTicketsForBooking(any());
    }

    @Test
    void cancelBookingThrowsWhenAlreadyCancelled() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId).attendee(attendee).event(event).tier(tier)
                .status(BookingStatus.CANCELLED).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(eventId, bookingId, attendee))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessage("only confirmed bookings can be cancelled");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private CreateBookingRequest bookingRequest(UUID tierId, int quantity) {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setTierId(tierId);
        request.setQuantity(quantity);
        return request;
    }
}
