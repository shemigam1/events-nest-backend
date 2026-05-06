package group.moniepoint.eventsnestserver.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RegisterResponse {
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
}
