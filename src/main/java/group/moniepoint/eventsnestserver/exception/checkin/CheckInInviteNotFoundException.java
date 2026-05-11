package group.moniepoint.eventsnestserver.exception.checkin;

import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;

public class CheckInInviteNotFoundException extends ResourceNotFoundException {
    public CheckInInviteNotFoundException() {
        super("check-in invite not found");
    }
}
