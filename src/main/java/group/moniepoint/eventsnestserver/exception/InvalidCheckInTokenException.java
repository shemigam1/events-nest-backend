package group.moniepoint.eventsnestserver.exception;

public class InvalidCheckInTokenException extends UnauthorizedException {
    public InvalidCheckInTokenException() {
        super("check-in token is invalid, revoked, or expired");
    }
}
