package group.moniepoint.eventsnestserver.auth.model;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(updatable = false, nullable = false, length = 12)
    private String id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "vendor_verified", nullable = false)
    @Builder.Default
    private boolean vendorVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "vendor_verification_status", nullable = false, length = 20)
    @Builder.Default
    private VendorVerificationStatus vendorVerificationStatus = VendorVerificationStatus.NOT_REQUESTED;

    @Column(name = "vendor_service_type", length = 100)
    private String vendorServiceType;

    @Column(name = "vendor_profile_description", columnDefinition = "TEXT")
    private String vendorProfileDescription;

    @Column(name = "vendor_verification_rejection_reason", columnDefinition = "TEXT")
    private String vendorVerificationRejectionReason;

    @Column(name = "vendor_verification_submitted_at")
    private LocalDateTime vendorVerificationSubmittedAt;

    @Column(name = "vendor_verified_at")
    private LocalDateTime vendorVerifiedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = NanoIdUtils.randomNanoId(
                    NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
                    NanoIdUtils.DEFAULT_ALPHABET,
                    12);
        }
    }
}
