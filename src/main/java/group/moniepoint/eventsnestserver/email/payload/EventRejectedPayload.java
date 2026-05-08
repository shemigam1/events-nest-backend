package group.moniepoint.eventsnestserver.email.payload;

public record EventRejectedPayload(
        String organiserName,
        String eventTitle,
        String reason
) {}
