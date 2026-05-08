package group.moniepoint.eventsnestserver.bookings.service;

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
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.models.MembershipStatus;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.bookings.event.BookingConfirmedEvent;
import group.moniepoint.eventsnestserver.bookings.kafka.BookingEventPublisher;
import group.moniepoint.eventsnestserver.exception.BookingCancellationForbiddenException;
import group.moniepoint.eventsnestserver.exception.BookingNotFoundException;
import group.moniepoint.eventsnestserver.exception.BookingNotCancellableException;
import group.moniepoint.eventsnestserver.exception.EventNotFoundException;
import group.moniepoint.eventsnestserver.exception.InsufficientTierCapacityException;
import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;
import group.moniepoint.eventsnestserver.exception.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.TicketTierNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.tickets.dto.response.TicketResponse;
import group.moniepoint.eventsnestserver.tickets.models.Ticket;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import group.moniepoint.eventsnestserver.tickets.service.TicketService;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final TicketTierRepository tierRepository;
    private final EventRespository eventRepository;
    private final EventMembershipRepository membershipRepository;
    private final TicketRepository ticketRepository;
    private final TicketService ticketService;
    private final BookingEventPublisher eventPublisher;

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

        TicketTier tier = tierRepository.findById(request.getTierId())
                .orElseThrow(TicketTierNotFoundException::new);

        if (!tier.getEvent().getId().equals(eventId)) {
            throw new TicketTierNotFoundException();
        }

        if (tier.getAvailableCapacity() < request.getQuantity()) {
            throw new InsufficientTierCapacityException();
        }

        // Decrement capacity (optimistic locking via @Version on TicketTier)
        tier.setAvailableCapacity(tier.getAvailableCapacity() - request.getQuantity());
        tierRepository.save(tier);

        BigDecimal totalAmount = tier.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));
        Booking booking = Booking.builder()
                .attendee(attendee)
                .event(event)
                .tier(tier)
                .quantity(request.getQuantity())
                .totalAmount(totalAmount)
                .status(BookingStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentReference("SIMULATED-" + UUID.randomUUID())
                .build();
        Booking savedBooking = bookingRepository.saveAndFlush(booking);

        List<Ticket> tickets = ticketService.issueTickets(savedBooking, tier, attendee, request.getQuantity());

        // Insert ATTENDEE membership idempotently
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

        eventPublisher.publishBookingConfirmed(new BookingConfirmedEvent(
                savedBooking.getId(),
                event.getId(),
                attendee.getId(),
                attendee.getEmail(),
                tier.getId(),
                tier.getName(),
                request.getQuantity(),
                totalAmount,
                savedBooking.getPaymentReference(),
                savedBooking.getCreatedAt()));

        EventsNestResponse<BookingResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Booking confirmed");
        response.setData(toBookingResponse(savedBooking, tickets));
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

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setPaymentStatus(PaymentStatus.REFUNDED);
        Booking saved = bookingRepository.save(booking);

        ticketService.refundTicketsForBooking(bookingId);

        TicketTier tier = booking.getTier();
        tier.setAvailableCapacity(tier.getAvailableCapacity() + booking.getQuantity());
        tierRepository.save(tier);

        membershipRepository.deleteByEventsIdAndUserIdAndRole(
                eventId, requestingUser.getId(), EventRole.ATTENDEE);

        List<Ticket> tickets = ticketRepository.findAllByBookingId(bookingId);

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

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private BookingResponse toBookingResponse(Booking booking, List<Ticket> tickets) {
        List<TicketResponse> ticketResponses = tickets.stream()
                .map(ticketService::toTicketResponse)
                .toList();

        return BookingResponse.builder()
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
                .createdAt(booking.getCreatedAt())
                .tickets(ticketResponses)
                .build();
    }
}
