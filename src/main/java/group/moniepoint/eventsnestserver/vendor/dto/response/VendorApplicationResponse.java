package group.moniepoint.eventsnestserver.vendor.dto.response;

import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VendorApplicationResponse {

    private UUID id;
    private UUID eventId;
    private String eventTitle;
    private String applicantId;
    private String applicantName;
    private String serviceType;
    private String description;
    private BigDecimal proposedAmount;
    private String status;
    private String reviewedById;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    public static VendorApplicationResponse from(VendorApplication a) {
        return VendorApplicationResponse.builder()
                .id(a.getId())
                .eventId(a.getEvent().getId())
                .eventTitle(a.getEvent().getTitle())
                .applicantId(a.getApplicant().getId())
                .applicantName(a.getApplicant().getFirstName() + " " + a.getApplicant().getLastName())
                .serviceType(a.getServiceType())
                .description(a.getDescription())
                .proposedAmount(a.getProposedAmount())
                .status(a.getStatus().name())
                .reviewedById(a.getReviewedBy() != null ? a.getReviewedBy().getId() : null)
                .reviewedAt(a.getReviewedAt())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
