package group.moniepoint.eventsnestserver.comments.service;

import group.moniepoint.eventsnestserver.admin.dto.response.PageResponse;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.comments.dto.request.CreateCommentRequest;
import group.moniepoint.eventsnestserver.comments.dto.request.UpdateCommentRequest;
import group.moniepoint.eventsnestserver.comments.dto.response.CommentResponse;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;

import java.util.List;
import java.util.UUID;

public interface CommentService {

    EventsNestResponse<CommentResponse> create(UUID eventId, CreateCommentRequest request, User caller);

    PageResponse<CommentResponse> listTopLevel(UUID eventId, int page, int size, User caller);

    List<CommentResponse> listReplies(UUID commentId, User caller);

    EventsNestResponse<CommentResponse> update(UUID commentId, UpdateCommentRequest request, User caller);

    EventsNestResponse<Void> delete(UUID commentId, User caller);

    /** Toggles a LIKE on/off for the calling user. Returns the latest state. */
    EventsNestResponse<CommentResponse> toggleLike(UUID commentId, User caller);
}
