package group.moniepoint.eventsnestserver.vendor.model;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.chat.model.Conversation;
import group.moniepoint.eventsnestserver.events.models.Events;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "vendor_inquiries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "organizer_id", "vendor_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorInquiry {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Events event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false, updatable = false)
    private User organizer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false, updatable = false)
    private User vendor;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "service_type", length = 100)
    private String serviceType;

    /** Auto-created DIRECT conversation between organizer and vendor. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InquiryStatus status = InquiryStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
