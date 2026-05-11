package group.moniepoint.eventsnestserver.checkin.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketCheckedInEvent(
        UUID ticketId,
        UUID eventId,
        UUID eventDayId,
        String eventTitle,
        String attendeeId,
        String qrCode,
        String seatNumber,
        String checkedInByLabel,
        LocalDateTime checkedInAt
) {}
