package group.moniepoint.eventsnestserver.exception.comments;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class CommentsNotEnabledException extends InvalidEventStateException {
    public CommentsNotEnabledException() {
        super("comments are not enabled for this event");
    }
}
