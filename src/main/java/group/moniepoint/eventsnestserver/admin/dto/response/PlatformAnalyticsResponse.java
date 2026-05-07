package group.moniepoint.eventsnestserver.admin.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@Builder
public class PlatformAnalyticsResponse {

    private Map<String, Long> eventsByStatus;
    private long totalBookings;
    private BigDecimal totalRevenue;
    private double checkInRate;
}
