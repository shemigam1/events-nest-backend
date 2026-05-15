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
import group.moniepoint.eventsnestserver.calendar.CalendarService;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.email.EmailOutbox;
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
import group.moniepoint.eventsnestserver.audit.publisher.AuditEventPublisher;
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
    @Mock private org.springframework.cache.CacheManager cacheManager;
    @Mock private AuditEventPublisher auditEventPublisher;
    @Mock private EmailOutbox emailOutbox;
    @Mock private CalendarService calendarService;
    @Mock private group.moniepoint.eventsnestserver.guestlist.repository.GuestRepository guestRepository;

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
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                guestRepository,
                cacheManager,
                auditEventPublisher,
                emailOutbox,
                calendarService);

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
        event.setVisibility(group.moniepoint.eventsnestserver.events.models.EventVisibility.PUBLIC);

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

    // ─── createBooking ────────────────────────────────────────────────────────────

    @Test
    void createBookingDecrementsTierCapacityAndIssuesTicketsImmediately() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) b.setId(UUID.randomUUID());
            return b;
        });
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, attendee.getId(), EventRole.ORGANIZER)).thenReturn(false);
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, attendee.getId(), EventRole.ATTENDEE)).thenReturn(false);
        when(ticketService.issueTickets(any(), any(), any(), anyInt())).thenReturn(List.of());

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

        // Tickets issued immediately, membership inserted, event published.
        verify(ticketService).issueTickets(any(), any(), any(), anyInt());
        verify(membershipRepository).save(any(EventMembership.class));
        verify(eventPublisher).publishBookingConfirmed(any());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("tickets issued");
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
        tier.setAvailableCapacity(47);

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
                .hasMessage("booking cannot be cancelled");
    }

    // ─── private events ───────────────────────────────────────────────────────────

    @Test
    void privateEventRejectsBookingWithoutAcceptedRsvp() {
        event.setVisibility(group.moniepoint.eventsnestserver.events.models.EventVisibility.PRIVATE);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, attendee.getId(), EventRole.ORGANIZER))
                .thenReturn(false);
        when(guestRepository.existsByEventIdAndEmailAndRsvpStatus(
                eventId, attendee.getEmail(),
                group.moniepoint.eventsnestserver.guestlist.model.RsvpStatus.ACCEPTED))
                .thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(eventId, bookingRequest(tierId, 1), attendee))
                .isInstanceOf(group.moniepoint.eventsnestserver.exception.event.PrivateEventBookingForbiddenException.class);

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void privateEventAllowsBookingWithAcceptedRsvp() {
        event.setVisibility(group.moniepoint.eventsnestserver.events.models.EventVisibility.PRIVATE);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, attendee.getId(), EventRole.ORGANIZER))
                .thenReturn(false);
        when(guestRepository.existsByEventIdAndEmailAndRsvpStatus(
                eventId, attendee.getEmail(),
                group.moniepoint.eventsnestserver.guestlist.model.RsvpStatus.ACCEPTED))
                .thenReturn(true);
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) b.setId(UUID.randomUUID());
            return b;
        });
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, attendee.getId(), EventRole.ATTENDEE))
                .thenReturn(false);
        when(ticketService.issueTickets(any(), any(), any(), anyInt())).thenReturn(List.of());

        bookingService.createBooking(eventId, bookingRequest(tierId, 1), attendee);

        verify(bookingRepository).saveAndFlush(any(Booking.class));
        verify(ticketService).issueTickets(any(), any(), any(), anyInt());
    }

    // ─── finalizeBookingPayment (webhook path) ────────────────────────────────────

    @Test
    void finalizeBookingPaymentIssuesTicketsAndPublishesEvent() {
        UUID bookingId = UUID.randomUUID();
        Booking pending = pendingBookingWithRef(bookingId, "STUB-finalize-1");
        when(bookingRepository.findByPaymentGatewayRef("STUB-finalize-1"))
                .thenReturn(Optional.of(pending));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketService.issueTickets(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, attendee.getId(), EventRole.ATTENDEE))
                .thenReturn(false);

        EventsNestResponse<BookingResponse> res = bookingService.finalizeBookingPayment("STUB-finalize-1");

        assertThat(res.isSuccess()).isTrue();
        assertThat(pending.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(pending.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(pending.getPaidAt()).isNotNull();
        verify(ticketService).issueTickets(pending, tier, attendee, 1);
        verify(eventPublisher).publishBookingConfirmed(any());
        verify(membershipRepository).save(any(EventMembership.class));
    }

    @Test
    void finalizeBookingPaymentIsIdempotentWhenAlreadyPaidWithTickets() {
        UUID bookingId = UUID.randomUUID();
        Booking alreadyPaid = pendingBookingWithRef(bookingId, "STUB-finalize-2");
        alreadyPaid.setPaymentStatus(PaymentStatus.PAID);
        Ticket existingTicket = Ticket.builder().id(UUID.randomUUID()).build();
        when(bookingRepository.findByPaymentGatewayRef("STUB-finalize-2"))
                .thenReturn(Optional.of(alreadyPaid));
        when(ticketRepository.findAllByBookingId(bookingId)).thenReturn(List.of(existingTicket));
        when(ticketService.toTicketResponse(existingTicket)).thenReturn(TicketResponse.builder().build());

        EventsNestResponse<BookingResponse> res = bookingService.finalizeBookingPayment("STUB-finalize-2");

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getMessage()).contains("already paid");
        verify(ticketService, never()).issueTickets(any(), any(), any(), anyInt());
        verify(eventPublisher, never()).publishBookingConfirmed(any());
    }

    @Test
    void finalizeBookingPaymentRecoversWhenPaidButTicketsMissing() {
        UUID bookingId = UUID.randomUUID();
        Booking paidNoTickets = pendingBookingWithRef(bookingId, "STUB-recover-1");
        paidNoTickets.setPaymentStatus(PaymentStatus.PAID);
        when(bookingRepository.findByPaymentGatewayRef("STUB-recover-1"))
                .thenReturn(Optional.of(paidNoTickets));
        when(ticketRepository.findAllByBookingId(bookingId)).thenReturn(List.of());
        when(ticketService.issueTickets(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, attendee.getId(), EventRole.ATTENDEE))
                .thenReturn(false);

        EventsNestResponse<BookingResponse> res = bookingService.finalizeBookingPayment("STUB-recover-1");

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getMessage()).contains("already paid");
        verify(ticketService).issueTickets(paidNoTickets, tier, attendee, 1);
        verify(membershipRepository).save(any(EventMembership.class));
        verify(eventPublisher, never()).publishBookingConfirmed(any());
    }

    // ─── markBookingFailed ───────────────────────────────────────────────────────

    @Test
    void markBookingFailedFlipsStatusAndRestoresCapacity() {
        UUID bookingId = UUID.randomUUID();
        Booking pending = pendingBookingWithRef(bookingId, "STUB-fail-1");
        tier.setAvailableCapacity(47);
        when(bookingRepository.findByPaymentGatewayRef("STUB-fail-1"))
                .thenReturn(Optional.of(pending));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.markBookingFailed("STUB-fail-1", "FAILED_TRANSACTION");

        assertThat(pending.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(tier.getAvailableCapacity()).isEqualTo(48);
        verify(tierRepository).save(tier);
    }

    @Test
    void markBookingFailedIsIdempotent() {
        UUID bookingId = UUID.randomUUID();
        Booking alreadyFailed = pendingBookingWithRef(bookingId, "STUB-fail-2");
        alreadyFailed.setPaymentStatus(PaymentStatus.FAILED);
        when(bookingRepository.findByPaymentGatewayRef("STUB-fail-2"))
                .thenReturn(Optional.of(alreadyFailed));

        EventsNestResponse<BookingResponse> res = bookingService.markBookingFailed("STUB-fail-2", "FAILED");

        assertThat(res.isSuccess()).isTrue();
        verify(tierRepository, never()).save(any());
    }

    @Test
    void markBookingFailedRefusesForPaidBookings() {
        UUID bookingId = UUID.randomUUID();
        Booking paid = pendingBookingWithRef(bookingId, "STUB-fail-3");
        paid.setPaymentStatus(PaymentStatus.PAID);
        when(bookingRepository.findByPaymentGatewayRef("STUB-fail-3"))
                .thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> bookingService.markBookingFailed("STUB-fail-3", "FAILED"))
                .isInstanceOf(InvalidEventStateException.class);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private CreateBookingRequest bookingRequest(UUID tierId, int quantity) {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setTierId(tierId);
        request.setQuantity(quantity);
        return request;
    }

    private Booking pendingBookingWithRef(UUID id, String paymentGatewayRef) {
        return Booking.builder()
                .id(id)
                .attendee(attendee)
                .event(event)
                .tier(tier)
                .quantity(1)
                .totalAmount(new BigDecimal("100.00"))
                .status(BookingStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentGatewayRef(paymentGatewayRef)
                .paymentReference(id.toString())
                .build();
    }
}
