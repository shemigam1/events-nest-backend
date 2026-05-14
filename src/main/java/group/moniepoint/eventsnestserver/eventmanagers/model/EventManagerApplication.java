package group.moniepoint.eventsnestserver.eventmanagers.model;

import group.moniepoint.eventsnestserver.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A user's application to become an event manager. One row per user
 * (re-applying overwrites). Admins review via /admin/event-managers/...
 * endpoints in Phase 3.3.2.
 */
@Entity
@Table(name = "event_manager_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventManagerApplication {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false, unique = true, updatable = false)
    private User applicant;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "past_events_count")
    private Integer pastEventsCount;

    @Column(columnDefinition = "TEXT")
    private String specialties;

    @Column(length = 255)
    private String location;

    @Column(name = "portfolio_url", length = 1024)
    private String portfolioUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ManagerApplicationStatus status = ManagerApplicationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @CreationTimestamp
    @Column(name = "applied_at", updatable = false, nullable = false)
    private LocalDateTime appliedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
