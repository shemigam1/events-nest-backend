package group.moniepoint.eventsnestserver.bookings.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record BookingConfirmedEvent(
        UUID bookingId,
        UUID eventId,
        String attendeeId,
        String attendeeEmail,
        UUID tierId,
        String tierName,
        int quantity,
        BigDecimal totalAmount,
        String paymentReference,
        LocalDateTime confirmedAt
) {}
