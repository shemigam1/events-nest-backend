package group.moniepoint.eventsnestserver.vendor.dto.response;

import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import group.moniepoint.eventsnestserver.vendor.model.VendorRating;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One entry in a vendor's schedule or completed-work list.
 * Represents a single ACCEPTED vendor application and its associated rating (if any).
 */
@Getter
@Builder
public class VendorScheduleItem {

    private UUID   applicationId;
    private UUID   eventId;
    private String eventTitle;
    private String eventVenue;
    private LocalDateTime eventStartTime;
    private LocalDateTime eventEndTime;
    private String serviceType;
    private BigDecimal agreedAmount;

    /** Whether the event has already ended (completed work vs upcoming). */
    private boolean completed;

    /** Rating given by the organiser after the event. Null if not yet rated. */
    private Integer ratingScore;
    private String  ratingComment;
    private LocalDateTime ratedAt;

    public static VendorScheduleItem from(VendorApplication app, VendorRating rating) {
        LocalDateTime now = LocalDateTime.now();
        boolean completed = app.getEvent().getEndTime().isBefore(now);

        return VendorScheduleItem.builder()
                .applicationId(app.getId())
                .eventId(app.getEvent().getId())
                .eventTitle(app.getEvent().getTitle())
                .eventVenue(app.getEvent().getVenue())
                .eventStartTime(app.getEvent().getStartTime())
                .eventEndTime(app.getEvent().getEndTime())
                .serviceType(app.getServiceType())
                .agreedAmount(app.getProposedAmount())
                .completed(completed)
                .ratingScore(rating != null ? rating.getScore() : null)
                .ratingComment(rating != null ? rating.getComment() : null)
                .ratedAt(rating != null ? rating.getCreatedAt() : null)
                .build();
    }
}
