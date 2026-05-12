package group.moniepoint.eventsnestserver.manager.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignManagerRequest {

    @NotBlank(message = "email is required")
    @Email(message = "must be a valid email address")
    private String email;
}
