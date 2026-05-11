package group.moniepoint.eventsnestserver.checkin.port;

import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CheckInTicketView(
        UUID id,
        UUID eventId,
        String eventTitle,
        String tierName,
        String seatNumber,
        String qrCode,
        TicketStatus status,
        /** Event-wide default when a day has no override. */
        LocalDateTime eventCheckInStartTime,
        /** Non-null when the tier is restricted to a single event day. */
        UUID tierEventDayId,
        String attendeeId,
        String attendeeFirstName,
        String attendeeLastName
) {}
