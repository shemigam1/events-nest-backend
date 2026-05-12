package group.moniepoint.eventsnestserver.exception.programme;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class ProgrammeNotEnabledException extends InvalidEventStateException {
    public ProgrammeNotEnabledException() {
        super("programme is not enabled for this event");
    }
}
