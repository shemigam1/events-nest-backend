package group.moniepoint.eventsnestserver.events.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UpdateEventRequest {

    @Size(max = 255)
    private String title;

    private String description;

    @Size(max = 255)
    private String venue;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
