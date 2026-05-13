package group.moniepoint.eventsnestserver.comments.model;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.events.models.Events;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A comment on an event. Replies are modelled by setting {@code parent} —
 * one level deep only. Deleted comments are soft-deleted (deleted_at NOT
 * NULL) so their replies remain in context with a "[Comment removed]"
 * placeholder.
 */
@Entity
@Table(name = "event_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventComment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Events event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    /** Null on top-level comments; set when this is a reply. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", updatable = false)
    private EventComment parent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Set on every edit so we can render an "edited" affordance. */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    /** Soft delete marker. When set, the body is hidden in responses. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isReply() {
        return parent != null;
    }
}
