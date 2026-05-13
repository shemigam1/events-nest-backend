package group.moniepoint.eventsnestserver.exception.comments;

import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;

public class CommentNotFoundException extends ResourceNotFoundException {
    public CommentNotFoundException() {
        super("comment not found");
    }
}
