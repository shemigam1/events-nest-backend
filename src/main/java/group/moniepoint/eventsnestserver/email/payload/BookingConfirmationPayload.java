package group.moniepoint.eventsnestserver.email.payload;

import java.math.BigDecimal;

/**
 * Stored as JSON in {@code email_jobs.payload_json} when the job's type is
 * {@code BOOKING_CONFIRMED}. The poller deserialises it and hands the
 * fields to {@code EmailService.sendBookingConfirmation}.
 */
public record BookingConfirmationPayload(
        String attendeeName,
        String eventTitle,
        String tierName,
        Integer quantity,
        BigDecimal totalAmount,
        String paymentReference
) {}
