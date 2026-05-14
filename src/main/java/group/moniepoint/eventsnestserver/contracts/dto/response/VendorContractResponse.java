package group.moniepoint.eventsnestserver.contracts.dto.response;

import group.moniepoint.eventsnestserver.contracts.model.ContractStatus;
import group.moniepoint.eventsnestserver.contracts.model.VendorContract;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VendorContractResponse {

    private UUID id;
    private UUID eventId;
    private String eventTitle;
    private String organizerId;
    private String organizerName;
    private String vendorId;
    private String vendorName;
    private UUID vendorApplicationId;
    private String title;
    private String description;
    private String terms;
    private BigDecimal amount;
    private ContractStatus status;
    private LocalDateTime signedAt;
    private LocalDateTime fundedAt;
    private LocalDateTime activatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime terminatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VendorContractResponse from(VendorContract c) {
        return VendorContractResponse.builder()
                .id(c.getId())
                .eventId(c.getEvent() != null ? c.getEvent().getId() : null)
                .eventTitle(c.getEvent() != null ? c.getEvent().getTitle() : null)
                .organizerId(c.getOrganizer().getId())
                .organizerName(c.getOrganizer().getFirstName() + " " + c.getOrganizer().getLastName())
                .vendorId(c.getVendor().getId())
                .vendorName(c.getVendor().getFirstName() + " " + c.getVendor().getLastName())
                .vendorApplicationId(c.getVendorApplication() != null ? c.getVendorApplication().getId() : null)
                .title(c.getTitle())
                .description(c.getDescription())
                .terms(c.getTerms())
                .amount(c.getAmount())
                .status(c.getStatus())
                .signedAt(c.getSignedAt())
                .fundedAt(c.getFundedAt())
                .activatedAt(c.getActivatedAt())
                .completedAt(c.getCompletedAt())
                .terminatedAt(c.getTerminatedAt())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
