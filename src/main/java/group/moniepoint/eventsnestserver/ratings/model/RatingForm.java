package group.moniepoint.eventsnestserver.ratings.model;

import group.moniepoint.eventsnestserver.events.models.Events;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "rating_forms",
        uniqueConstraints = @UniqueConstraint(name = "uk_rating_forms_event_id", columnNames = "event_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RatingForm {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Events event;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "collect_anonymous", nullable = false)
    @Builder.Default
    private boolean collectAnonymous = false;

    @Column(name = "send_delay_hours", nullable = false)
    @Builder.Default
    private int sendDelayHours = 24;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<RatingQuestion> questions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
