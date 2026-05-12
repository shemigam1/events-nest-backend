package group.moniepoint.eventsnestserver.events.models;

import group.moniepoint.eventsnestserver.auth.model.User;
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


@Entity
@Table(
        name = "event_memberships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "event_id", "role"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventMembership {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Events events;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by", nullable = true)
    private User assignedBy;

    /**
     * EVENT_MANAGER-only: scoped permissions for this membership. Stored as
     * a Postgres text[] (see V13). Service layer reads/writes
     * {@link group.moniepoint.eventsnestserver.eventmanagers.model.ManagerPermission}
     * values and converts via {@code .name()} / {@code .valueOf()}.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "permissions", columnDefinition = "text[]")
    @Builder.Default
    private Set<String> permissions = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
