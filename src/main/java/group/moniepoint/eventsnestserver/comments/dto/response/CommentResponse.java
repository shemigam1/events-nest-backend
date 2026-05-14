package group.moniepoint.eventsnestserver.comments.dto.response;

import group.moniepoint.eventsnestserver.comments.model.EventComment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A comment as the frontend sees it. {@code body} is blanked out for
 * deleted comments — clients should render a "[Comment removed]" stub.
 * {@code currentUserLiked} reflects whether the caller has reacted with
 * LIKE; flips on toggle.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

    private UUID    id;
    private UUID    eventId;
    private UUID    parentId;

    private String  authorId;
    private String  authorName;
    /** ORGANIZER / MANAGER / CHECKIN_STAFF / ATTENDEE — or null if the author has no membership on this event. */
    private String  authorRoleOnEvent;

    private String  body;
    private boolean deleted;
    private boolean edited;

    private long    likeCount;
    private long    replyCount;
    private boolean currentUserLiked;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CommentResponse from(EventComment c,
                                       String authorRoleOnEvent,
                                       long likeCount,
                                       long replyCount,
                                       boolean currentUserLiked) {
        return CommentResponse.builder()
                .id(c.getId())
                .eventId(c.getEvent().getId())
                .parentId(c.getParent() != null ? c.getParent().getId() : null)
                .authorId(c.getAuthor().getId())
                .authorName(authorDisplayName(c))
                .authorRoleOnEvent(authorRoleOnEvent)
                .body(c.isDeleted() ? null : c.getBody())
                .deleted(c.isDeleted())
                .edited(c.getEditedAt() != null)
                .likeCount(likeCount)
                .replyCount(replyCount)
                .currentUserLiked(currentUserLiked)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private static String authorDisplayName(EventComment c) {
        String first = c.getAuthor().getFirstName();
        String last  = c.getAuthor().getLastName();
        if ((first == null || first.isBlank()) && (last == null || last.isBlank())) {
            return c.getAuthor().getEmail();
        }
        return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }
}
