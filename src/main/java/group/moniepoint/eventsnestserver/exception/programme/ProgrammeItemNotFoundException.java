package group.moniepoint.eventsnestserver.exception.programme;

import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;

public class ProgrammeItemNotFoundException extends ResourceNotFoundException {
    public ProgrammeItemNotFoundException() {
        super("programme item not found");
    }
}
