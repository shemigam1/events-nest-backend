package group.moniepoint.eventsnestserver.vendor.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Full vendor profile as seen by an event organiser.
 *
 * <ul>
 *   <li>{@code upcomingSchedule} — accepted applications on events that haven't started yet.</li>
 *   <li>{@code completedWork}    — accepted applications on events that have already ended,
 *                                  including any rating the organiser gave.</li>
 * </ul>
 */
@Getter
@Builder
public class VendorProfileResponse {

    private String vendorId;
    private String vendorName;
    private String email;
    private String serviceType;
    private String profileDescription;
    private boolean vendorVerified;

    /** Average star rating across all completed events (null if no ratings yet). */
    private Double averageRating;
    private long   totalRatings;

    private List<VendorScheduleItem> upcomingSchedule;
    private List<VendorScheduleItem> completedWork;
}
