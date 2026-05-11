package group.moniepoint.eventsnestserver.events.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "event_configs",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_configs_event_id", columnNames = "event_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventConfig {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Events event;

    @Column(name = "ticketing_enabled", nullable = false)
    @Builder.Default
    private boolean ticketingEnabled = true;

    @Column(name = "guest_list_enabled", nullable = false)
    @Builder.Default
    private boolean guestListEnabled = false;

    @Column(name = "programme_enabled", nullable = false)
    @Builder.Default
    private boolean programmeEnabled = false;

    @Column(name = "ratings_enabled", nullable = false)
    @Builder.Default
    private boolean ratingsEnabled = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
