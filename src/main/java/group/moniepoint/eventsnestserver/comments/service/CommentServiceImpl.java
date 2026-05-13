package group.moniepoint.eventsnestserver.comments.service;

import group.moniepoint.eventsnestserver.admin.dto.response.PageResponse;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.comments.dto.request.CreateCommentRequest;
import group.moniepoint.eventsnestserver.comments.dto.request.UpdateCommentRequest;
import group.moniepoint.eventsnestserver.comments.dto.response.CommentResponse;
import group.moniepoint.eventsnestserver.comments.model.CommentReaction;
import group.moniepoint.eventsnestserver.comments.model.EventComment;
import group.moniepoint.eventsnestserver.comments.model.ReactionType;
import group.moniepoint.eventsnestserver.comments.repository.CommentReactionRepository;
import group.moniepoint.eventsnestserver.comments.repository.EventCommentRepository;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.comments.CommentForbiddenException;
import group.moniepoint.eventsnestserver.exception.comments.CommentNotFoundException;
import group.moniepoint.eventsnestserver.exception.comments.CommentsNotEnabledException;
import group.moniepoint.eventsnestserver.exception.event.EventConfigNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.notifications.model.NotificationType;
import group.moniepoint.eventsnestserver.notifications.service.NotificationServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Comment / reaction service.
 *
 * <p>Permission model:
 * <ul>
 *   <li>Anyone authenticated can post on a PUBLISHED event.</li>
 *   <li>Authors edit/delete their own comments.</li>
 *   <li>Organisers + managers can delete any comment (moderation).</li>
 *   <li>Reads are open to any authenticated caller; we don't gate on
 *       attendance — public events have public comment threads.</li>
 * </ul>
 *
 * <p>Notifications: COMMENT_RECEIVED fires for the event organiser when
 * someone (not themselves) leaves a top-level comment. COMMENT_REPLY fires
 * for the parent comment's author when someone (not themselves) replies.
 * Both go through the existing {@link NotificationServiceImpl#createIfAbsent}
 * dedupe contract so a double-tap on submit doesn't double-notify.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final EventCommentRepository    commentRepository;
    private final CommentReactionRepository reactionRepository;
    private final EventRespository          eventRepository;
    private final EventConfigRepository     configRepository;
    private final EventMembershipRepository membershipRepository;
    private final NotificationServiceImpl   notifications;

    @Override
    @Transactional
    public EventsNestResponse<CommentResponse> create(UUID eventId, CreateCommentRequest request, User caller) {
        Events event = findEventOrThrow(eventId);
        assertCommentsEnabled(eventId);
        assertEventAcceptsComments(event);

        EventComment parent = null;
        if (request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                    .filter(p -> p.getEvent().getId().equals(eventId))
                    .orElseThrow(CommentNotFoundException::new);
            // No nesting beyond one level. A reply to a reply lifts to the
            // root parent — flatter threads are easier to read.
            if (parent.getParent() != null) {
                parent = parent.getParent();
            }
        }

        EventComment saved = commentRepository.save(EventComment.builder()
                .event(event)
                .author(caller)
                .parent(parent)
                .body(request.getBody().trim())
                .build());

        fireCreationNotifications(event, saved, caller);

        EventsNestResponse<CommentResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("comment posted");
        response.setData(toResponse(saved, caller));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> listTopLevel(UUID eventId, int page, int size, User caller) {
        findEventOrThrow(eventId);
        assertCommentsEnabled(eventId);

        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        Page<EventComment> result = commentRepository.findTopLevelByEvent(eventId, pageable);
        return PageResponse.from(result, c -> toResponse(c, caller));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> listReplies(UUID commentId, User caller) {
        EventComment parent = commentRepository.findById(commentId).orElseThrow(CommentNotFoundException::new);
        assertCommentsEnabled(parent.getEvent().getId());
        return commentRepository.findRepliesByParent(commentId).stream()
                .map(c -> toResponse(c, caller))
                .toList();
    }

    @Override
    @Transactional
    public EventsNestResponse<CommentResponse> update(UUID commentId, UpdateCommentRequest request, User caller) {
        EventComment comment = commentRepository.findById(commentId).orElseThrow(CommentNotFoundException::new);
        if (comment.isDeleted()) {
            throw new CommentForbiddenException("comment has been deleted");
        }
        if (!comment.getAuthor().getId().equals(caller.getId())) {
            throw new CommentForbiddenException("only the author can edit this comment");
        }
        comment.setBody(request.getBody().trim());
        comment.setEditedAt(LocalDateTime.now());
        EventComment saved = commentRepository.save(comment);

        EventsNestResponse<CommentResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("comment updated");
        response.setData(toResponse(saved, caller));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<Void> delete(UUID commentId, User caller) {
        EventComment comment = commentRepository.findById(commentId).orElseThrow(CommentNotFoundException::new);
        if (comment.isDeleted()) {
            // Idempotent — second delete is a no-op.
            EventsNestResponse<Void> response = new EventsNestResponse<>();
            response.setSuccess(true);
            response.setMessage("comment already deleted");
            return response;
        }

        boolean isAuthor    = comment.getAuthor().getId().equals(caller.getId());
        boolean canModerate = isOrganiserOrManager(comment.getEvent().getId(), caller);
        if (!isAuthor && !canModerate) {
            throw new CommentForbiddenException("you don't have permission to delete this comment");
        }

        comment.setDeletedAt(LocalDateTime.now());
        // Blank the body — soft-deleted comments are read-only stubs. We keep
        // the row so reply threads stay coherent.
        comment.setBody("");
        commentRepository.save(comment);

        EventsNestResponse<Void> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("comment deleted");
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<CommentResponse> toggleLike(UUID commentId, User caller) {
        EventComment comment = commentRepository.findById(commentId).orElseThrow(CommentNotFoundException::new);
        if (comment.isDeleted()) {
            throw new CommentForbiddenException("cannot react to a deleted comment");
        }
        assertCommentsEnabled(comment.getEvent().getId());

        var existing = reactionRepository
                .findByCommentIdAndUserIdAndType(commentId, caller.getId(), ReactionType.LIKE);

        if (existing.isPresent()) {
            reactionRepository.delete(existing.get());
        } else {
            reactionRepository.save(CommentReaction.builder()
                    .comment(comment)
                    .user(caller)
                    .type(ReactionType.LIKE)
                    .build());
        }

        EventsNestResponse<CommentResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("reaction toggled");
        response.setData(toResponse(comment, caller));
        return response;
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CommentResponse toResponse(EventComment c, User caller) {
        long likes   = reactionRepository.countByCommentIdAndType(c.getId(), ReactionType.LIKE);
        long replies = c.isReply() ? 0L : commentRepository.countByParentId(c.getId());
        boolean userLiked = caller != null
                && reactionRepository.existsByCommentIdAndUserIdAndType(c.getId(), caller.getId(), ReactionType.LIKE);
        // The enum is declared in priority order (ORGANIZER first), so the
        // smallest ordinal among the author's memberships is the highest-
        // ranking role to display next to their name.
        String role = membershipRepository
                .findAllByEventsIdAndUserId(c.getEvent().getId(), c.getAuthor().getId())
                .stream()
                .map(EventMembership::getRole)
                .min(java.util.Comparator.comparingInt(EventRole::ordinal))
                .map(EventRole::name)
                .orElse(null);
        return CommentResponse.from(c, role, likes, replies, userLiked);
    }

    private Events findEventOrThrow(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }

    private void assertCommentsEnabled(UUID eventId) {
        EventConfig config = configRepository.findByEventId(eventId)
                .orElseThrow(EventConfigNotFoundException::new);
        if (!config.isCommentsEnabled()) {
            throw new CommentsNotEnabledException();
        }
    }

    /** Drafts and cancelled events don't accept new comments. Replies + reads still work for historical context. */
    private void assertEventAcceptsComments(Events event) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new CommentForbiddenException("comments are only open on published events");
        }
    }

    private boolean isOrganiserOrManager(UUID eventId, User user) {
        return membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER)
                || membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.MANAGER);
    }

    private void fireCreationNotifications(Events event, EventComment created, User author) {
        // Don't ping the organiser about their own comment.
        String organiserId = event.getCreatedBy() != null ? event.getCreatedBy().getId() : null;
        if (created.getParent() == null
                && organiserId != null
                && !organiserId.equals(author.getId())) {
            notifications.createIfAbsent(
                    organiserId,
                    NotificationType.COMMENT_RECEIVED,
                    "comment:" + created.getId(),
                    "New comment on " + event.getTitle(),
                    truncate(created.getBody(), 140)
            );
        }

        // Reply → ping the parent's author (unless they replied to themselves).
        if (created.getParent() != null) {
            String parentAuthor = created.getParent().getAuthor().getId();
            if (!parentAuthor.equals(author.getId())) {
                notifications.createIfAbsent(
                        parentAuthor,
                        NotificationType.COMMENT_REPLY,
                        "comment-reply:" + created.getId(),
                        "New reply to your comment on " + event.getTitle(),
                        truncate(created.getBody(), 140)
                );
            }
        }
    }

    private static int clampSize(int size) {
        if (size <= 0) return 20;
        return Math.min(size, 50);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
