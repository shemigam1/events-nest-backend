package group.moniepoint.eventsnestserver.email.payload;

public record EventApprovedPayload(
        String organiserName,
        String eventTitle
) {}
