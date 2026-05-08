package group.moniepoint.eventsnestserver.admin.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventApprovedEvent(
        UUID eventId,
        String eventTitle,
        String organiserId,
        LocalDateTime approvedAt
) {}
