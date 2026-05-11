package group.moniepoint.eventsnestserver.exception.checkin;

import group.moniepoint.eventsnestserver.exception.UnauthorizedException;

public class InvalidCheckInTokenException extends UnauthorizedException {
    public InvalidCheckInTokenException() {
        super("check-in token is invalid, revoked, or expired");
    }
}
