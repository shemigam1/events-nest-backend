package group.moniepoint.eventsnestserver.email.payload;

public record PasswordResetPayload(
        String name,
        String resetToken
) {}
