package group.moniepoint.eventsnestserver.vendor.dto.response;

import group.moniepoint.eventsnestserver.vendor.model.InquiryStatus;
import group.moniepoint.eventsnestserver.vendor.model.VendorInquiry;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VendorInquiryResponse {

    private UUID id;
    private UUID eventId;
    private String eventTitle;
    private String organizerId;
    private String organizerName;
    private String vendorId;
    private String vendorName;
    private String message;
    private String serviceType;
    private UUID conversationId;
    private InquiryStatus status;
    private LocalDateTime createdAt;

    public static VendorInquiryResponse from(VendorInquiry i) {
        return VendorInquiryResponse.builder()
                .id(i.getId())
                .eventId(i.getEvent().getId())
                .eventTitle(i.getEvent().getTitle())
                .organizerId(i.getOrganizer().getId())
                .organizerName(i.getOrganizer().getFirstName() + " " + i.getOrganizer().getLastName())
                .vendorId(i.getVendor().getId())
                .vendorName(i.getVendor().getFirstName() + " " + i.getVendor().getLastName())
                .message(i.getMessage())
                .serviceType(i.getServiceType())
                .conversationId(i.getConversation() != null ? i.getConversation().getId() : null)
                .status(i.getStatus())
                .createdAt(i.getCreatedAt())
                .build();
    }
}
