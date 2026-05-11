package group.moniepoint.eventsnestserver.email.payload;

import java.util.UUID;

public record GuestRsvpInvitePayload(
        String guestName,
        String eventTitle,
        UUID eventId,
        String rsvpToken
) {}
