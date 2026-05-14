package group.moniepoint.eventsnestserver.email.payload;

import java.math.BigDecimal;

/**
 * Extended booking confirmation payload with calendar event data.
 * Stored as JSON in {@code email_jobs.payload_json} when the job's type is
 * {@code BOOKING_CONFIRMED_WITH_CALENDAR}. The poller deserialises it and
 * hands the fields to {@code EmailService.sendBookingConfirmationWithCalendar}.
 *
 * Includes Google Calendar URL and event details for calendar integration.
 */
public record BookingConfirmationWithCalendarPayload(
        String attendeeName,
        String eventTitle,
        String tierName,
        Integer quantity,
        BigDecimal totalAmount,
        String paymentReference,
        String googleCalendarUrl,
        String eventDate,
        String eventLocation
) {}
