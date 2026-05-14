package group.moniepoint.eventsnestserver.notifications.consumer;

import group.moniepoint.eventsnestserver.admin.event.EventApprovedEvent;
import group.moniepoint.eventsnestserver.admin.event.EventRejectedEvent;
import group.moniepoint.eventsnestserver.bookings.event.BookingConfirmedEvent;
import group.moniepoint.eventsnestserver.checkin.event.TicketCheckedInEvent;
import group.moniepoint.eventsnestserver.contracts.event.ContractSignedEvent;
import group.moniepoint.eventsnestserver.email.EmailOutbox;
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
 *   2. Enqueue an EmailJob for the relevant user. Async — picked up by
 *      EmailJobPoller and sent via Resend, so a slow/down email provider
 *      can never block this consumer thread.
 *   3. Push to any open SSE streams so connected clients can update their
 *      UI without polling.
 *
 * The SSE push happens after the audit insert intentionally — if the
 * consumer crashes mid-handler, the audit row is already committed and
 * the redelivered message skips the insert (idempotent) but still fires
 * SSE + email.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaConsumer {

    private final NotificationServiceImpl notificationService;
    private final SseDispatcher sseDispatcher;
    private final EmailOutbox emailOutbox;

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

        emailOutbox.enqueueEventApproved(event);
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

        emailOutbox.enqueueEventRejected(event);
        sseDispatcher.onEventRejected(event);
    }

    @KafkaListener(topics = "${contract-signed.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onContractSigned(ContractSignedEvent event) {
        notificationService.createIfAbsent(
                event.organizerId(),
                NotificationType.CONTRACT_SIGNED,
                event.contractId().toString(),
                "Contract signed",
                String.format("Vendor has signed the contract \"%s\" for %s.", event.contractTitle(), event.eventTitle()));

        sseDispatcher.onContractSigned(event);
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

        // No email for check-in — the SSE push and notification row are enough;
        // attendees don't need a second email after they've already been admitted.
        sseDispatcher.onTicketCheckedIn(event);
    }
}
