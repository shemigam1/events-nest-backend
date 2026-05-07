package group.moniepoint.eventsnestserver.tiers.models;

import group.moniepoint.eventsnestserver.events.models.Events;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_tiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketTier {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Events event;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "row_prefix", nullable = false, length = 10)
    private String rowPrefix;

    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    @Column(name = "seats_per_row", nullable = false)
    private Integer seatsPerRow;

    @Column(name = "total_capacity", nullable = false)
    private Integer totalCapacity;

    @Column(name = "available_capacity", nullable = false)
    private Integer availableCapacity;

    @Version
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
