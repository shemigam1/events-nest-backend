package group.moniepoint.eventsnestserver.exception.auth;

import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;

public class UserNotFoundException extends ResourceNotFoundException {
    public UserNotFoundException() {
        super("user not found");
    }
}
