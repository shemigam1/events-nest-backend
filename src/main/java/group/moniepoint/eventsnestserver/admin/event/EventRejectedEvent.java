package group.moniepoint.eventsnestserver.admin.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventRejectedEvent(
        UUID eventId,
        String eventTitle,
        String organiserId,
        String reason,
        LocalDateTime rejectedAt
) {}
