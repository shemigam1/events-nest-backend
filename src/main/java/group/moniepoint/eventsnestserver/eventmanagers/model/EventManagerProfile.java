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
 * Public profile of an approved event manager. Created on application
 * approval and shown to organisers browsing who to invite.
 */
@Entity
@Table(name = "event_manager_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventManagerProfile {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
    private User user;

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

    /** True if an admin has suspended this manager (quality / abuse). */
    @Column(nullable = false)
    @Builder.Default
    private boolean suspended = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
