package group.moniepoint.eventsnestserver.email.payload;

import java.util.UUID;

public record RatingRequestPayload(
        String attendeeName,
        String eventTitle,
        UUID formId
) {}
