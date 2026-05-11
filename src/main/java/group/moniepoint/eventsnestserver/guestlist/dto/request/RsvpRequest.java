package group.moniepoint.eventsnestserver.guestlist.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RsvpRequest {

    @NotBlank(message = "token is required")
    private String token;

    /** ACCEPTED or DECLINED. */
    @NotNull(message = "response is required")
    private String response;
}
