package group.moniepoint.eventsnestserver.admin.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InviteAdminRequest {

    @NotBlank(message = "email is required")
    @Email(message = "must be a valid email address")
    private String email;
}
