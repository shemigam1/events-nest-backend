package group.moniepoint.eventsnestserver.vendor.dto.response;

import group.moniepoint.eventsnestserver.auth.model.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VendorVerificationResponse {

    private String userId;
    private String firstName;
    private String lastName;
    private String email;
    private boolean vendorVerified;
    private String status;
    private String serviceType;
    private String description;
    private String rejectionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime verifiedAt;

    public static VendorVerificationResponse from(User user) {
        return VendorVerificationResponse.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .vendorVerified(user.isVendorVerified())
                .status(user.getVendorVerificationStatus().name())
                .serviceType(user.getVendorServiceType())
                .description(user.getVendorProfileDescription())
                .rejectionReason(user.getVendorVerificationRejectionReason())
                .submittedAt(user.getVendorVerificationSubmittedAt())
                .verifiedAt(user.getVendorVerifiedAt())
                .build();
    }
}
