package group.moniepoint.eventsnestserver.vendor.model;

import group.moniepoint.eventsnestserver.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An organiser's rating of a vendor after their event has ended.
 * One rating per vendor application — organiser can update it but not add a second.
 */
@Entity
@Table(name = "vendor_ratings",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_vendor_rating_application",
               columnNames = "vendor_application_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorRating {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_application_id", nullable = false, updatable = false)
    private VendorApplication vendorApplication;

    /** The organiser who wrote the rating. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rated_by", nullable = false, updatable = false)
    private User ratedBy;

    /** 1–5 stars. */
    @Column(nullable = false)
    private int score;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
