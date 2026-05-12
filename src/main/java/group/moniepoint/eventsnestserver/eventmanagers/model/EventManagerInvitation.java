package group.moniepoint.eventsnestserver.eventmanagers.model;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.events.models.Events;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An invitation from an organiser to an approved event manager to help run
 * a specific event. On acceptance, an {@code event_memberships} row is
 * created with role=EVENT_MANAGER and the same permissions array.
 */
@Entity
@Table(
        name = "event_manager_invitations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_event_manager_invitations_event_manager",
                columnNames = {"event_id", "manager_user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventManagerInvitation {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Events event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_user_id", nullable = false, updatable = false)
    private User managerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false, updatable = false)
    private User invitedBy;

    /**
     * Permissions to be granted on acceptance. Mirrors the structure on
     * {@code event_memberships.permissions}. Stored as Postgres text[].
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "permissions", nullable = false, columnDefinition = "text[]")
    @Builder.Default
    private Set<String> permissions = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ManagerInvitationStatus status = ManagerInvitationStatus.PENDING;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
