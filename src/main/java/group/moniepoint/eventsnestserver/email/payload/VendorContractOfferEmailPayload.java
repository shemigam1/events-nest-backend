package group.moniepoint.eventsnestserver.email.payload;

import java.math.BigDecimal;
import java.util.UUID;

public record VendorContractOfferEmailPayload(
        String vendorName,
        String organizerName,
        String eventTitle,
        UUID eventId,
        String contractTitle,
        BigDecimal amount,
        UUID conversationId,
        UUID contractId
) {}
