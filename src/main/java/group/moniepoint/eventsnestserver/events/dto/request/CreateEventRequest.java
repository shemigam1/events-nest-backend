package group.moniepoint.eventsnestserver.events.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter

public class CreateEventRequest {
    @NotBlank
    @Size(max=255)
    private String title;

    @NotBlank
    @Size(max =255)
    private String venue;


    @NotNull
    @Future
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;
}
