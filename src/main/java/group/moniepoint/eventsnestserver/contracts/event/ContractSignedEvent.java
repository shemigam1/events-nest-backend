package group.moniepoint.eventsnestserver.contracts.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContractSignedEvent(
        UUID contractId,
        UUID eventId,
        String eventTitle,
        String contractTitle,
        BigDecimal amount,
        String vendorId,
        String vendorEmail,
        String organizerId,
        String organizerEmail,
        LocalDateTime signedAt
) {}
