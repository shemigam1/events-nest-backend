package group.moniepoint.eventsnestserver.exception;

public class CheckInInviteNotFoundException extends ResourceNotFoundException {
    public CheckInInviteNotFoundException() {
        super("check-in invite not found");
    }
}
