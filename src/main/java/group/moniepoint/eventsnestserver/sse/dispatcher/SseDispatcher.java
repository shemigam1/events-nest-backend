package group.moniepoint.eventsnestserver.sse.dispatcher;

import group.moniepoint.eventsnestserver.admin.event.EventApprovedEvent;
import group.moniepoint.eventsnestserver.admin.event.EventRejectedEvent;
import group.moniepoint.eventsnestserver.bookings.event.BookingConfirmedEvent;
import group.moniepoint.eventsnestserver.checkin.event.TicketCheckedInEvent;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.sse.registry.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Translates Kafka domain events into SSE pushes. Looks up secondary
 * recipients (e.g. organiser of a booked event) when the event payload
 * doesn't carry them, then routes to the per-user emitter registry.
 *
 * The event-name strings ({@code booking.confirmed}, etc.) are the
 * <em>contract</em> the frontend listens for — keep them stable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SseDispatcher {

    public static final String EVENT_BOOKING_CONFIRMED = "booking.confirmed";
    public static final String EVENT_EVENT_APPROVED    = "event.approved";
    public static final String EVENT_EVENT_REJECTED    = "event.rejected";
    public static final String EVENT_TICKET_CHECKED_IN = "ticket.checked-in";

    private final SseEmitterRegistry registry;
    private final EventRespository eventRepository;

    public void onBookingConfirmed(BookingConfirmedEvent event) {
        // Attendee always gets it — drives their /me/bookings + /me/tickets refresh.
        registry.pushTo(event.attendeeId(), EVENT_BOOKING_CONFIRMED, event);

        // Organiser gets it too — drives their console "tickets sold" counter live.
        eventRepository.findById(event.eventId()).ifPresent(e -> {
            if (e.getCreatedBy() != null) {
                registry.pushTo(e.getCreatedBy().getId(), EVENT_BOOKING_CONFIRMED, event);
            }
        });
    }

    public void onEventApproved(EventApprovedEvent event) {
        registry.pushTo(event.organiserId(), EVENT_EVENT_APPROVED, event);
    }

    public void onEventRejected(EventRejectedEvent event) {
        registry.pushTo(event.organiserId(), EVENT_EVENT_REJECTED, event);
    }

    public void onTicketCheckedIn(TicketCheckedInEvent event) {
        // Attendee — drives their /me/tickets refresh + "you've been checked in" toast.
        registry.pushTo(event.attendeeId(), EVENT_TICKET_CHECKED_IN, event);

        // Organiser — drives the live activity feed on their event console.
        eventRepository.findById(event.eventId()).ifPresent(e -> {
            if (e.getCreatedBy() != null) {
                registry.pushTo(e.getCreatedBy().getId(), EVENT_TICKET_CHECKED_IN, event);
            }
        });
    }
}
