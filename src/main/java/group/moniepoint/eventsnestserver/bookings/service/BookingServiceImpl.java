package group.moniepoint.eventsnestserver.bookings.service;

import group.moniepoint.eventsnestserver.audit.AuditAction;
import group.moniepoint.eventsnestserver.audit.AuditEntityType;
import group.moniepoint.eventsnestserver.audit.event.AuditEvent;
import group.moniepoint.eventsnestserver.audit.publisher.AuditEventPublisher;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.bookings.dto.request.CreateBookingRequest;
import group.moniepoint.eventsnestserver.bookings.dto.response.BookingResponse;
import group.moniepoint.eventsnestserver.bookings.models.Booking;
import group.moniepoint.eventsnestserver.bookings.models.BookingStatus;
import group.moniepoint.eventsnestserver.bookings.models.PaymentStatus;
import group.moniepoint.eventsnestserver.bookings.repository.BookingRepository;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.EventVisibility;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.models.MembershipStatus;
import group.moniepoint.eventsnestserver.guestlist.model.RsvpStatus;
import group.moniepoint.eventsnestserver.guestlist.repository.GuestRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.bookings.event.BookingConfirmedEvent;
import group.moniepoint.eventsnestserver.bookings.kafka.BookingEventPublisher;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.booking.BookingCancellationForbiddenException;
import group.moniepoint.eventsnestserver.exception.booking.BookingNotCancellableException;
import group.moniepoint.eventsnestserver.exception.booking.BookingNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.PrivateEventBookingForbiddenException;
import group.moniepoint.eventsnestserver.payments.MonnifyClient;
import group.moniepoint.eventsnestserver.payments.MonnifyPaymentStatus;
import group.moniepoint.eventsnestserver.payments.dto.InitializeTransactionRequest;
import group.moniepoint.eventsnestserver.payments.dto.InitializeTransactionResponse;
import group.moniepoint.eventsnestserver.payments.dto.VerifyTransactionResponse;
import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.ticket.InsufficientTierCapacityException;
import group.moniepoint.eventsnestserver.exception.ticket.TicketTierNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.tickets.dto.response.TicketResponse;
import group.moniepoint.eventsnestserver.tickets.models.Ticket;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import group.moniepoint.eventsnestserver.tickets.service.TicketService;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import lombok.AllArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final TicketTierRepository tierRepository;
    private final EventRespository eventRepository;
    private final EventMembershipRepository membershipRepository;
    private final TicketRepository ticketRepository;
    private final TicketService ticketService;
    private final BookingEventPublisher eventPublisher;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final GuestRepository guestRepository;
    private final MonnifyClient monnifyClient;
    private final String monnifyRedirectUrl;
    private final CacheManager cacheManager;
    private final AuditEventPublisher auditEventPublisher;

    /** Constructor injection — paymentRedirectUrl read from monnify.redirect-url. */
    public BookingServiceImpl(
            BookingRepository bookingRepository,
            TicketTierRepository tierRepository,
            EventRespository eventRepository,
            EventMembershipRepository membershipRepository,
            TicketRepository ticketRepository,
            TicketService ticketService,
            BookingEventPublisher eventPublisher,
            io.micrometer.core.instrument.MeterRegistry meterRegistry,
            GuestRepository guestRepository,
            MonnifyClient monnifyClient,
            CacheManager cacheManager,
            @org.springframework.beans.factory.annotation.Value("${monnify.redirect-url:http://localhost:5173/payment-result}") String monnifyRedirectUrl,
            AuditEventPublisher auditEventPublisher) {
        this.bookingRepository = bookingRepository;
        this.tierRepository = tierRepository;
        this.eventRepository = eventRepository;
        this.membershipRepository = membershipRepository;
        this.ticketRepository = ticketRepository;
        this.ticketService = ticketService;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.guestRepository = guestRepository;
        this.monnifyClient = monnifyClient;
        this.cacheManager = cacheManager;
        this.monnifyRedirectUrl = monnifyRedirectUrl;
        this.auditEventPublisher = auditEventPublisher;
    }

    private void evictEventDetail(UUID eventId) {
        Cache cache = cacheManager.getCache("event-detail");
        if (cache != null) cache.evict(eventId);
    }

    @Override
    @Transactional
    public EventsNestResponse<BookingResponse> createBooking(UUID eventId,
                                                             CreateBookingRequest request,
                                                             User attendee) {
        Events event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new InvalidEventStateException("only published events can be booked");
        }

        boolean isOrganizer = membershipRepository
                .existsByEventsIdAndUserIdAndRole(eventId, attendee.getId(), EventRole.ORGANIZER);
        if (isOrganizer) {
            throw new UnauthorizedException("organizers cannot book tickets for their own event");
        }

        // PRD §5.5 — PRIVATE events require an accepted RSVP.
        if (event.getVisibility() == EventVisibility.PRIVATE) {
            boolean hasAcceptedRsvp = guestRepository.existsByEventIdAndEmailAndRsvpStatus(
                    eventId, attendee.getEmail(), RsvpStatus.ACCEPTED);
            if (!hasAcceptedRsvp) {
                throw new PrivateEventBookingForbiddenException();
            }
        }

        TicketTier tier = tierRepository.findById(request.getTierId())
                .orElseThrow(TicketTierNotFoundException::new);

        if (!tier.getEvent().getId().equals(eventId)) {
            throw new TicketTierNotFoundException();
        }

        if (tier.getAvailableCapacity() < request.getQuantity()) {
            throw new InsufficientTierCapacityException();
        }

        // Reserve capacity up-front (optimistic locking via @Version on TicketTier).
        // If payment fails or times out, markBookingFailed restores it.
        tier.setAvailableCapacity(tier.getAvailableCapacity() - request.getQuantity());
        tierRepository.save(tier);
        evictEventDetail(eventId);

        BigDecimal totalAmount = tier.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));
        Booking booking = Booking.builder()
                .attendee(attendee)
                .event(event)
                .tier(tier)
                .quantity(request.getQuantity())
                .totalAmount(totalAmount)
                .status(BookingStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        Booking savedBooking = bookingRepository.saveAndFlush(booking);

        // Hand off to Monnify to get a checkout URL.
        InitializeTransactionResponse monnify = monnifyClient.initializeTransaction(
                InitializeTransactionRequest.builder()
                        .amount(totalAmount)
                        .currencyCode("NGN")
                        .paymentReference(savedBooking.getId().toString())
                        .customerEmail(attendee.getEmail())
                        .customerName(combineName(attendee.getFirstName(), attendee.getLastName()))
                        .paymentDescription("Tickets — " + event.getTitle())
                        .redirectUrl(monnifyRedirectUrl)
                        .build());

        savedBooking.setMonnifyTransactionRef(monnify.getTransactionReference());
        savedBooking.setPaymentReference(savedBooking.getId().toString());
        savedBooking = bookingRepository.saveAndFlush(savedBooking);

        // No tickets issued, no membership inserted, no Kafka — those happen
        // in finalizeBookingPayment when Monnify confirms payment.

        BookingResponse data = toBookingResponse(savedBooking, java.util.List.of());
        data.setPaymentUrl(monnify.getCheckoutUrl());

        EventsNestResponse<BookingResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Booking created — complete payment to receive tickets");
        response.setData(data);
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<BookingResponse> finalizeBookingPayment(String monnifyTransactionRef) {
        Booking booking = bookingRepository.findByMonnifyTransactionRef(monnifyTransactionRef)
                .orElseThrow(BookingNotFoundException::new);

        // Idempotent: already-PAID bookings short-circuit. Same response shape
        // so the webhook can be called any number of times safely.
        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            List<Ticket> existing = ticketRepository.findAllByBookingId(booking.getId());
            EventsNestResponse<BookingResponse> idempotent = new EventsNestResponse<>();
            idempotent.setSuccess(true);
            idempotent.setMessage("Booking already paid");
            idempotent.setData(toBookingResponse(booking, existing));
            return idempotent;
        }

        if (booking.getPaymentStatus() == PaymentStatus.FAILED
                || booking.getPaymentStatus() == PaymentStatus.REFUNDED
                || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidEventStateException("booking is not in a state that can be finalized");
        }

        VerifyTransactionResponse verify = monnifyClient.verifyTransaction(monnifyTransactionRef);
        if (verify.getStatus() != MonnifyPaymentStatus.PAID) {
            // Payment hasn't actually completed. Don't issue tickets.
            EventsNestResponse<BookingResponse> response = new EventsNestResponse<>();
            response.setSuccess(false);
            response.setMessage("Payment not yet confirmed by Monnify (status=" + verify.getStatus() + ")");
            response.setData(toBookingResponse(booking, java.util.List.of()));
            return response;
        }

        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setPaidAt(LocalDateTime.now());
        Booking saved = bookingRepository.saveAndFlush(booking);

        TicketTier tier = booking.getTier();
        User attendee = booking.getAttendee();
        List<Ticket> tickets = ticketService.issueTickets(saved, tier, attendee, booking.getQuantity());

        boolean alreadyAttendee = membershipRepository
                .existsByEventsIdAndUserIdAndRole(booking.getEvent().getId(), attendee.getId(), EventRole.ATTENDEE);
        if (!alreadyAttendee) {
            membershipRepository.save(EventMembership.builder()
                    .user(attendee)
                    .events(booking.getEvent())
                    .role(EventRole.ATTENDEE)
                    .status(MembershipStatus.ACTIVE)
                    .build());
        }

        eventPublisher.publishBookingConfirmed(new BookingConfirmedEvent(
                saved.getId(),
                booking.getEvent().getId(),
                booking.getEvent().getTitle(),
                attendee.getId(),
                attendee.getEmail(),
                tier.getId(),
                tier.getName(),
                booking.getQuantity(),
                booking.getTotalAmount(),
                saved.getPaymentReference(),
                saved.getCreatedAt()));

        auditEventPublisher.publish(AuditEvent.of(
                attendee.getId(), attendee.getRole().name(),
                AuditAction.BOOKING_CONFIRMED, AuditEntityType.BOOKING, saved.getId().toString(),
                Map.of("eventId",     booking.getEvent().getId().toString(),
                       "tierId",      tier.getId().toString(),
                       "quantity",    booking.getQuantity(),
                       "totalAmount", booking.getTotalAmount().toPlainString())));

        meterRegistry.counter("eventsnest.bookings.confirmed").increment();
        meterRegistry.counter("eventsnest.bookings.revenue")
                .increment(booking.getTotalAmount().doubleValue());

        EventsNestResponse<BookingResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Payment confirmed — tickets issued");
        response.setData(toBookingResponse(saved, tickets));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<BookingResponse> markBookingFailed(String monnifyTransactionRef, String reason) {
        Booking booking = bookingRepository.findByMonnifyTransactionRef(monnifyTransactionRef)
                .orElseThrow(BookingNotFoundException::new);

        // Idempotent: already-FAILED is a no-op.
        if (booking.getPaymentStatus() == PaymentStatus.FAILED) {
            EventsNestResponse<BookingResponse> idempotent = new EventsNestResponse<>();
            idempotent.setSuccess(true);
            idempotent.setMessage("Booking already marked failed");
            idempotent.setData(toBookingResponse(booking, java.util.List.of()));
            return idempotent;
        }
        if (booking.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new InvalidEventStateException("only PENDING bookings can be marked failed");
        }

        booking.setPaymentStatus(PaymentStatus.FAILED);
        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.saveAndFlush(booking);

        // Restore reserved capacity so other attendees can book the seats.
        TicketTier tier = booking.getTier();
        tier.setAvailableCapacity(tier.getAvailableCapacity() + booking.getQuantity());
        tierRepository.save(tier);
        evictEventDetail(tier.getEvent().getId());

        EventsNestResponse<BookingResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Booking marked failed — capacity restored");
        response.setData(toBookingResponse(saved, java.util.List.of()));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<BookingResponse> cancelBooking(UUID eventId,
                                                             UUID bookingId,
                                                             User requestingUser) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(BookingNotFoundException::new);

        if (!booking.getEvent().getId().equals(eventId)) {
            throw new BookingNotFoundException();
        }
        if (!booking.getAttendee().getId().equals(requestingUser.getId())) {
            throw new BookingCancellationForbiddenException();
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingNotCancellableException();
        }

        // Cancellation has two flavours now:
        //   - PENDING booking: user abandoned checkout. No tickets exist,
        //     no membership was inserted, no Kafka event ever fired. Just
        //     restore capacity and mark cancelled.
        //   - PAID booking: existing refund flow — tickets refunded,
        //     membership removed, capacity restored.
        boolean wasPending = booking.getPaymentStatus() == PaymentStatus.PENDING;

        booking.setStatus(BookingStatus.CANCELLED);
        if (!wasPending) {
            booking.setPaymentStatus(PaymentStatus.REFUNDED);
        }
        Booking saved = bookingRepository.save(booking);

        if (!wasPending) {
            ticketService.refundTicketsForBooking(bookingId);
        }

        TicketTier tier = booking.getTier();
        tier.setAvailableCapacity(tier.getAvailableCapacity() + booking.getQuantity());
        tierRepository.save(tier);
        evictEventDetail(eventId);

        if (!wasPending) {
            membershipRepository.deleteByEventsIdAndUserIdAndRole(
                    eventId, requestingUser.getId(), EventRole.ATTENDEE);
        }

        List<Ticket> tickets = ticketRepository.findAllByBookingId(bookingId);

        auditEventPublisher.publish(AuditEvent.of(
                requestingUser.getId(), requestingUser.getRole().name(),
                AuditAction.BOOKING_CANCELLED, AuditEntityType.BOOKING, bookingId.toString(),
                Map.of("eventId",    eventId.toString(),
                       "wasPending", wasPending)));

        EventsNestResponse<BookingResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Booking cancelled");
        response.setData(toBookingResponse(saved, tickets));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(User user) {
        return bookingRepository.findAllByAttendeeId(user.getId())
                .stream()
                .map(b -> toBookingResponse(b, ticketRepository.findAllByBookingId(b.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsForEventByOrganiser(UUID eventId, User requestingUser) {
        // Same membership check pattern as EventServiceImpl / TicketTierServiceImpl.
        boolean isOrganizer = membershipRepository
                .existsByEventsIdAndUserIdAndRole(eventId, requestingUser.getId(), EventRole.ORGANIZER);
        if (!isOrganizer) {
            throw new UnauthorizedException("only the event organizer can view bookings");
        }

        return bookingRepository.findAllByEventIdOrderByCreatedAtDesc(eventId)
                .stream()
                .map(b -> toBookingResponse(b, ticketRepository.findAllByBookingId(b.getId())))
                .toList();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private BookingResponse toBookingResponse(Booking booking, List<Ticket> tickets) {
        List<TicketResponse> ticketResponses = tickets.stream()
                .map(ticketService::toTicketResponse)
                .toList();

        BookingResponse.BookingResponseBuilder builder = BookingResponse.builder()
                .id(booking.getId())
                .eventId(booking.getEvent() != null ? booking.getEvent().getId() : null)
                .eventTitle(booking.getEvent() != null ? booking.getEvent().getTitle() : null)
                .tierId(booking.getTier() != null ? booking.getTier().getId() : null)
                .tierName(booking.getTier() != null ? booking.getTier().getName() : null)
                .quantity(booking.getQuantity())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus())
                .paymentStatus(booking.getPaymentStatus())
                .paymentReference(booking.getPaymentReference())
                .transactionReference(booking.getMonnifyTransactionRef())
                .createdAt(booking.getCreatedAt())
                .tickets(ticketResponses);

        // Populate attendee details — exposed to both /me/bookings (the
        // attendee themselves) and /organizer/events/{id}/bookings.
        if (booking.getAttendee() != null) {
            String first = booking.getAttendee().getFirstName();
            String last = booking.getAttendee().getLastName();
            builder.attendeeId(booking.getAttendee().getId())
                    .attendeeFirstName(first)
                    .attendeeLastName(last)
                    .attendeeName(combineName(first, last))
                    .attendeeEmail(booking.getAttendee().getEmail());
        }

        return builder.build();
    }

    private static String combineName(String first, String last) {
        StringBuilder sb = new StringBuilder();
        if (first != null && !first.isBlank()) sb.append(first.trim());
        if (last != null && !last.isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(last.trim());
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
