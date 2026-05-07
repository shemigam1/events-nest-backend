package group.moniepoint.eventsnestserver.exception;

public class UserNotFoundException extends ResourceNotFoundException {
    public UserNotFoundException() {
        super("user not found");
    }
}
