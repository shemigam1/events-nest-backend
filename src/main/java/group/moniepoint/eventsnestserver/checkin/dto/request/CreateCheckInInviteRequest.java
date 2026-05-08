package group.moniepoint.eventsnestserver.checkin.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateCheckInInviteRequest {

    @NotBlank(message = "name is required")
    private String name;

    @Email(message = "must be a valid email address")
    private String email;
}
