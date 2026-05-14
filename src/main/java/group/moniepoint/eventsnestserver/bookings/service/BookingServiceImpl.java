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
import group.moniepoint.eventsnestserver.calendar.CalendarEventData;
import group.moniepoint.eventsnestserver.calendar.CalendarService;
import group.moniepoint.eventsnestserver.email.EmailOutbox;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.booking.BookingCancellationForbiddenException;
import group.moniepoint.eventsnestserver.exception.booking.BookingNotCancellableException;
import group.moniepoint.eventsnestserver.exception.booking.BookingNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.PrivateEventBookingForbiddenException;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
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
    private final CacheManager cacheManager;
    private final AuditEventPublisher auditEventPublisher;
    private final EmailOutbox emailOutbox;
    private final CalendarService calendarService;

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
            CacheManager cacheManager,
            AuditEventPublisher auditEventPublisher,
            EmailOutbox emailOutbox,
            CalendarService calendarService) {
        this.bookingRepository = bookingRepository;
        this.tierRepository = tierRepository;
        this.eventRepository = eventRepository;
        this.membershipRepository = membershipRepository;
        this.ticketRepository = ticketRepository;
        this.ticketService = ticketService;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.guestRepository = guestRepository;
        this.cacheManager = cacheManager;
        this.auditEventPublisher = auditEventPublisher;
        this.emailOutbox = emailOutbox;
        this.calendarService = calendarService;
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
                .paymentStatus(PaymentStatus.PAID)
                .paidAt(LocalDateTime.now())
                .paymentReference(UUID.randomUUID().toString())
                .build();
        Booking saved = bookingRepository.saveAndFlush(booking);

        List<Ticket> tickets = ticketService.issueTickets(saved, tier, attendee, request.getQuantity());

        boolean alreadyAttendee = membershipRepository
                .existsByEventsIdAndUserIdAndRole(eventId, attendee.getId(), EventRole.ATTENDEE);
        if (!alreadyAttendee) {
            membershipRepository.save(EventMembership.builder()
                    .user(attendee)
                    .events(event)
                    .role(EventRole.ATTENDEE)
                    .status(MembershipStatus.ACTIVE)
                    .build());
        }

        BookingConfirmedEvent confirmedEvent = new BookingConfirmedEvent(
                saved.getId(),
                event.getId(),
                event.getTitle(),
                attendee.getId(),
                attendee.getEmail(),
                tier.getId(),
                tier.getName(),
                request.getQuantity(),
                totalAmount,
                saved.getPaymentReference(),
                saved.getCreatedAt());

        eventPublisher.publishBookingConfirmed(confirmedEvent);

        try {
            CalendarEventData calendarData = CalendarEventData.builder()
                    .eventTitle(event.getTitle() + " - " + tier.getName() + " Ticket")
                    .startTime(event.getStartTime())
                    .endTime(event.getEndTime())
                    .location(event.getVenue())
                    .bookingId(saved.getId().toString())
                    .attendeeEmail(attendee.getEmail())
                    .build();
            String googleCalendarUrl = calendarService.generateGoogleCalendarUrl(calendarData);
            String eventDate = calendarService.formatDateForEmail(event.getStartTime());
            emailOutbox.enqueueBookingConfirmationWithCalendar(
                    confirmedEvent, googleCalendarUrl, eventDate, event.getVenue());
        } catch (Exception e) {
            log.warn("Could not enqueue booking confirmation email for booking {}: {}",
                    saved.getId(), e.getMessage());
            try {
                emailOutbox.enqueueBookingConfirmation(confirmedEvent);
            } catch (Exception ex) {
                log.error("Fallback email enqueue also failed for booking {}: {}",
                        saved.getId(), ex.getMessage());
            }
        }

        auditEventPublisher.publish(AuditEvent.of(
                attendee.getId(), attendee.getRole().name(),
                AuditAction.BOOKING_CONFIRMED, AuditEntityType.BOOKING, saved.getId().toString(),
                Map.of("eventId",     event.getId().toString(),
                       "tierId",      tier.getId().toString(),
                       "quantity",    request.getQuantity(),
                       "totalAmount", totalAmount.toPlainString())));

        meterRegistry.counter("eventsnest.bookings.confirmed").increment();
        meterRegistry.counter("eventsnest.bookings.revenue")
                .increment(totalAmount.doubleValue());

        EventsNestResponse<BookingResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Booking confirmed — tickets issued");
        response.setData(toBookingResponse(saved, tickets));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<BookingResponse> finalizeBookingPayment(String paymentGatewayRef) {
        Booking booking = bookingRepository.findByPaymentGatewayRef(paymentGatewayRef)
                .orElseThrow(BookingNotFoundException::new);

        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            List<Ticket> existing = ticketRepository.findAllByBookingId(booking.getId());
            if (existing.isEmpty()) {
                log.warn("Booking {} is PAID but has no tickets — re-issuing", booking.getId());
                TicketTier recTier = booking.getTier();
                User recAttendee = booking.getAttendee();
                existing = ticketService.issueTickets(booking, recTier, recAttendee, booking.getQuantity());
                boolean alreadyMember = membershipRepository
                        .existsByEventsIdAndUserIdAndRole(booking.getEvent().getId(), recAttendee.getId(), EventRole.ATTENDEE);
                if (!alreadyMember) {
                    membershipRepository.save(EventMembership.builder()
                            .user(recAttendee)
                            .events(booking.getEvent())
                            .role(EventRole.ATTENDEE)
                            .status(MembershipStatus.ACTIVE)
                            .build());
                }
            }
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

        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaidAt(LocalDateTime.now());
        Booking saved = bookingRepository.saveAndFlush(booking);

        TicketTier tier = booking.getTier();
        User bookingAttendee = booking.getAttendee();
        List<Ticket> tickets = ticketService.issueTickets(saved, tier, bookingAttendee, booking.getQuantity());

        boolean alreadyAttendeeCheck = membershipRepository
                .existsByEventsIdAndUserIdAndRole(booking.getEvent().getId(), bookingAttendee.getId(), EventRole.ATTENDEE);
        if (!alreadyAttendeeCheck) {
            membershipRepository.save(EventMembership.builder()
                    .user(bookingAttendee)
                    .events(booking.getEvent())
                    .role(EventRole.ATTENDEE)
                    .status(MembershipStatus.ACTIVE)
                    .build());
        }

        BookingConfirmedEvent confirmedEvent = new BookingConfirmedEvent(
                saved.getId(),
                booking.getEvent().getId(),
                booking.getEvent().getTitle(),
                bookingAttendee.getId(),
                bookingAttendee.getEmail(),
                tier.getId(),
                tier.getName(),
                booking.getQuantity(),
                booking.getTotalAmount(),
                saved.getPaymentReference(),
                saved.getCreatedAt());

        eventPublisher.publishBookingConfirmed(confirmedEvent);

        try {
            Events ev = booking.getEvent();
            CalendarEventData calendarData = CalendarEventData.builder()
                    .eventTitle(ev.getTitle() + " - " + tier.getName() + " Ticket")
                    .startTime(ev.getStartTime())
                    .endTime(ev.getEndTime())
                    .location(ev.getVenue())
                    .bookingId(saved.getId().toString())
                    .attendeeEmail(bookingAttendee.getEmail())
                    .build();
            String googleCalendarUrl = calendarService.generateGoogleCalendarUrl(calendarData);
            String eventDate = calendarService.formatDateForEmail(ev.getStartTime());
            emailOutbox.enqueueBookingConfirmationWithCalendar(
                    confirmedEvent, googleCalendarUrl, eventDate, ev.getVenue());
        } catch (Exception e) {
            log.warn("Could not enqueue booking confirmation email for booking {}: {}",
                    saved.getId(), e.getMessage());
            try {
                emailOutbox.enqueueBookingConfirmation(confirmedEvent);
            } catch (Exception ex) {
                log.error("Fallback email enqueue also failed for booking {}: {}",
                        saved.getId(), ex.getMessage());
            }
        }

        auditEventPublisher.publish(AuditEvent.of(
                bookingAttendee.getId(), bookingAttendee.getRole().name(),
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
    public EventsNestResponse<BookingResponse> markBookingFailed(String paymentGatewayRef, String reason) {
        Booking booking = bookingRepository.findByPaymentGatewayRef(paymentGatewayRef)
                .orElseThrow(BookingNotFoundException::new);

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
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingNotCancellableException();
        }

        boolean wasPending = booking.getStatus() == BookingStatus.PENDING_PAYMENT;

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
                .transactionReference(booking.getPaymentGatewayRef())
                .createdAt(booking.getCreatedAt())
                .tickets(ticketResponses);

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
