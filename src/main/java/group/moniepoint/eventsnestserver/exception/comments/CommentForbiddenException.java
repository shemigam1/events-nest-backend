package group.moniepoint.eventsnestserver.exception.comments;

import group.moniepoint.eventsnestserver.exception.UnauthorizedException;

public class CommentForbiddenException extends UnauthorizedException {
    public CommentForbiddenException(String message) {
        super(message);
    }
}
