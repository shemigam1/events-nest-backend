package group.moniepoint.eventsnestserver.email.payload;

import java.util.UUID;

public record VendorInquiryEmailPayload(
        String vendorName,
        String organizerName,
        String eventTitle,
        UUID eventId,
        String message,
        String serviceType,
        UUID conversationId
) {}
