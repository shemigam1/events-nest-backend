package group.moniepoint.eventsnestserver.email.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailJob {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "to_email", nullable = false, length = 255)
    private String toEmail;

    /**
     * Discriminator. Older rows persisted before this column existed will
     * read back as null — the poller treats null as STAFF_INVITE for
     * backward compatibility.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", length = 32)
    private EmailJobType type;

    /**
     * Serialised type-specific payload for non-staff-invite jobs. Format
     * is one of the records under {@code email.payload.*}; the poller
     * deserializes by {@link #type}.
     */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    // ── Legacy staff-invite columns. Kept nullable for backward compat
    //    with rows written before the payload-json approach.

    @Column(name = "staff_name", length = 100)
    private String staffName;

    @Column(name = "raw_token", columnDefinition = "TEXT")
    private String rawToken;

    @Column(name = "event_title", length = 255)
    private String eventTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EmailJobStatus status = EmailJobStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 3;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
