package group.moniepoint.eventsnestserver.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CompleteAdminInvitationRequest {

    @NotBlank(message = "token is required")
    private String token;

    @NotBlank(message = "first name is required")
    private String firstName;

    @NotBlank(message = "last name is required")
    private String lastName;

    @NotBlank(message = "password is required")
    @Size(min = 8, message = "password must be at least 8 characters")
    private String password;
}
