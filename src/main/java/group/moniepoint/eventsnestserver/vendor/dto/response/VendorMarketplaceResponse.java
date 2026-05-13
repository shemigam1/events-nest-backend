package group.moniepoint.eventsnestserver.vendor.dto.response;

import group.moniepoint.eventsnestserver.auth.model.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * A single vendor card in the marketplace listing.
 * Contains enough info for an organiser to decide whether to invite/contact a vendor.
 */
@Getter
@Builder
public class VendorMarketplaceResponse {

    private String vendorId;
    private String vendorName;
    private String serviceType;
    private String profileDescription;
    private boolean vendorVerified;

    /** Average star score (1–5), null if the vendor has never been rated. */
    private Double averageRating;
    private long   totalRatings;

    /** Total number of events this vendor has successfully completed. */
    private long completedEvents;

    private LocalDateTime memberSince;

    public static VendorMarketplaceResponse from(User vendor, Double averageRating,
                                                  long totalRatings, long completedEvents) {
        Double rounded = averageRating != null
                ? Math.round(averageRating * 10.0) / 10.0
                : null;

        return VendorMarketplaceResponse.builder()
                .vendorId(vendor.getId())
                .vendorName(vendor.getFirstName() + " " + vendor.getLastName())
                .serviceType(vendor.getVendorServiceType())
                .profileDescription(vendor.getVendorProfileDescription())
                .vendorVerified(vendor.isVendorVerified())
                .averageRating(rounded)
                .totalRatings(totalRatings)
                .completedEvents(completedEvents)
                .memberSince(vendor.getCreatedAt())
                .build();
    }
}
