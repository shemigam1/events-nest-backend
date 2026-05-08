package group.moniepoint.eventsnestserver.notifications.consumer;

import group.moniepoint.eventsnestserver.admin.event.EventApprovedEvent;
import group.moniepoint.eventsnestserver.admin.event.EventRejectedEvent;
import group.moniepoint.eventsnestserver.bookings.event.BookingConfirmedEvent;
import group.moniepoint.eventsnestserver.checkin.event.TicketCheckedInEvent;
import group.moniepoint.eventsnestserver.email.EmailOutbox;
import group.moniepoint.eventsnestserver.notifications.model.NotificationType;
import group.moniepoint.eventsnestserver.notifications.service.NotificationServiceImpl;
import group.moniepoint.eventsnestserver.sse.dispatcher.SseDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumerTest {

    @Mock private NotificationServiceImpl notificationService;
    @Mock private SseDispatcher sseDispatcher;
    @Mock private EmailOutbox emailOutbox;

    private NotificationKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationKafkaConsumer(notificationService, sseDispatcher, emailOutbox);
    }

    @Test
    void onBookingConfirmed_dispatchesNotificationToAttendee() {
        UUID bookingId = UUID.randomUUID();
        BookingConfirmedEvent event = new BookingConfirmedEvent(
                bookingId, UUID.randomUUID(), "Spring Boot Conf",
                "user-attendee", "ada@x.com",
                UUID.randomUUID(), "VIP", 2, new BigDecimal("100000"),
                "SIMULATED-x", LocalDateTime.now());

        consumer.onBookingConfirmed(event);

        verify(notificationService).createIfAbsent(
                eq("user-attendee"),
                eq(NotificationType.BOOKING_CONFIRMED),
                eq(bookingId.toString()),
                eq("Booking confirmed"),
                contains("VIP"));
    }

    @Test
    void onEventApproved_dispatchesNotificationToOrganiser() {
        UUID eventId = UUID.randomUUID();
        EventApprovedEvent event = new EventApprovedEvent(
                eventId, "Spring Boot Conf", "user-organiser", LocalDateTime.now());

        consumer.onEventApproved(event);

        verify(notificationService).createIfAbsent(
                eq("user-organiser"),
                eq(NotificationType.EVENT_APPROVED),
                eq(eventId.toString()),
                eq("Event approved"),
                contains("Spring Boot Conf"));
    }

    @Test
    void onEventRejected_includesReasonInNotificationMessage() {
        UUID eventId = UUID.randomUUID();
        EventRejectedEvent event = new EventRejectedEvent(
                eventId, "Spring Boot Conf", "user-organiser",
                "venue not verified", LocalDateTime.now());

        consumer.onEventRejected(event);

        verify(notificationService).createIfAbsent(
                eq("user-organiser"),
                eq(NotificationType.EVENT_REJECTED),
                eq(eventId.toString()),
                eq("Event rejected"),
                contains("venue not verified"));
    }

    @Test
    void onTicketCheckedIn_dispatchesNotificationToTicketAttendee() {
        UUID ticketId = UUID.randomUUID();
        TicketCheckedInEvent event = new TicketCheckedInEvent(
                ticketId, UUID.randomUUID(), "Spring Boot Conf",
                "user-attendee", "qr-code", "V1-1",
                "Ada (staff)", LocalDateTime.now());

        consumer.onTicketCheckedIn(event);

        verify(notificationService).createIfAbsent(
                eq("user-attendee"),
                eq(NotificationType.CHECKIN_SUCCESS),
                eq(ticketId.toString()),
                eq("Checked in"),
                any());
    }
}
