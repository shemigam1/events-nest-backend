package group.moniepoint.eventsnestserver.notifications.consumer;

import group.moniepoint.eventsnestserver.admin.event.EventApprovedEvent;
import group.moniepoint.eventsnestserver.admin.event.EventRejectedEvent;
import group.moniepoint.eventsnestserver.bookings.event.BookingConfirmedEvent;
import group.moniepoint.eventsnestserver.checkin.event.TicketCheckedInEvent;
import group.moniepoint.eventsnestserver.notifications.model.NotificationType;
import group.moniepoint.eventsnestserver.notifications.service.NotificationServiceImpl;
import group.moniepoint.eventsnestserver.sse.dispatcher.SseDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Single fan-out point for every domain event the platform cares about.
 * Each handler does three things in order, with each side-effect isolated
 * so a failure in one path doesn't take the others down:
 *
 *   1. Persist a Notification audit row (idempotent, race-safe via unique
 *      constraint on type+dedupeKey — see PRD §8.5).
 *   2. (TODO) Enqueue an EmailJob for the relevant user.
 *   3. Push to any open SSE streams so connected clients can update their
 *      UI without polling.
 *
 * SSE pushes after the audit insert is intentional — if the consumer crashes
 * mid-handler, the audit row is already committed and the next consumer that
 * picks up the redelivered message will skip the insert (idempotent) and
 * still fire the SSE push.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaConsumer {

    private final NotificationServiceImpl notificationService;
    private final SseDispatcher sseDispatcher;

    @KafkaListener(topics = "${booking.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        notificationService.createIfAbsent(
                event.attendeeId(),
                NotificationType.BOOKING_CONFIRMED,
                event.bookingId().toString(),
                "Booking confirmed",
                String.format("Your booking for %d × %s ticket(s) is confirmed (ref: %s).",
                        event.quantity(), event.tierName(), event.paymentReference()));

        sseDispatcher.onBookingConfirmed(event);
    }

    @KafkaListener(topics = "${event-approved.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onEventApproved(EventApprovedEvent event) {
        notificationService.createIfAbsent(
                event.organiserId(),
                NotificationType.EVENT_APPROVED,
                event.eventId().toString(),
                "Event approved",
                String.format("\"%s\" has been approved and is now published.", event.eventTitle()));

        sseDispatcher.onEventApproved(event);
    }

    @KafkaListener(topics = "${event-rejected.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onEventRejected(EventRejectedEvent event) {
        notificationService.createIfAbsent(
                event.organiserId(),
                NotificationType.EVENT_REJECTED,
                event.eventId().toString(),
                "Event rejected",
                String.format("\"%s\" was returned to draft. Reason: %s", event.eventTitle(), event.reason()));

        sseDispatcher.onEventRejected(event);
    }

    @KafkaListener(topics = "${checkin.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onTicketCheckedIn(TicketCheckedInEvent event) {
        notificationService.createIfAbsent(
                event.attendeeId(),
                NotificationType.CHECKIN_SUCCESS,
                event.ticketId().toString(),
                "Checked in",
                String.format("You have been checked in to %s, seat %s.",
                        event.eventTitle(), event.seatNumber()));

        sseDispatcher.onTicketCheckedIn(event);
    }
}
