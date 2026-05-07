package group.moniepoint.eventsnestserver.events.common.validation;

import java.time.LocalDateTime;

public interface HasStartEndTime {
    LocalDateTime getStartTime();
    LocalDateTime getEndTime();
}
