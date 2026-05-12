package group.moniepoint.eventsnestserver.exception.ratings;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class RatingsNotEnabledException extends InvalidEventStateException {
    public RatingsNotEnabledException() {
        super("ratings are not enabled for this event");
    }
}
