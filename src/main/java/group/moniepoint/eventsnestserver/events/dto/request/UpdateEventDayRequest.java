package group.moniepoint.eventsnestserver.events.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * PATCH semantics: only fields present in the request are updated.
 * end-after-start is enforced at the service layer because here either
 * field may be omitted (and the comparison happens against the persisted
 * value).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEventDayRequest {

    @Size(max = 255)
    private String title;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime checkInStartTime;

    @Size(max = 255)
    private String venue;
}
