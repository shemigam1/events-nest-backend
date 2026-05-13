package group.moniepoint.eventsnestserver.comments.model;

import group.moniepoint.eventsnestserver.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single user's reaction to a comment. (comment_id, user_id, type) is
 * unique — the same user can't like the same comment twice. Liking is
 * idempotent: calling the toggle endpoint with an existing row removes
 * it (unlike), otherwise inserts.
 */
@Entity
@Table(
        name = "comment_reactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_comment_reaction",
                columnNames = {"comment_id", "user_id", "type"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentReaction {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false, updatable = false)
    private EventComment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReactionType type;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
