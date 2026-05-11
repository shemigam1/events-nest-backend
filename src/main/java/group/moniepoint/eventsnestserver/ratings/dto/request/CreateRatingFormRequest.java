package group.moniepoint.eventsnestserver.ratings.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateRatingFormRequest {

    @NotBlank(message = "title is required")
    private String title;

    private boolean collectAnonymous = false;

    /** Hours after the event ends before dispatch emails are sent. */
    private int sendDelayHours = 24;
}
