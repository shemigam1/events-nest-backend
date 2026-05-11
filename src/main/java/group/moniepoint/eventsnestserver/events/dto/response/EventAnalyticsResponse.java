package group.moniepoint.eventsnestserver.events.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventAnalyticsResponse {

    private long ticketsSold;
    private BigDecimal totalRevenue;
    private double checkInRate;

    private List<DayCheckIn> checkInsByDay;
    private List<TierStats> ticketsByTier;
    private List<DateCount> bookingsByDate;

    /** Null when guest list module is disabled. */
    private Map<String, Long> rsvpSummary;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DayCheckIn {
        private int dayNumber;
        private long count;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TierStats {
        private String tierName;
        private long sold;
        private int totalCapacity;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DateCount {
        private String date;
        private long count;
    }
}
