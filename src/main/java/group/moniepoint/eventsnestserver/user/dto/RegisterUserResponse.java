package group.moniepoint.eventsnestserver.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterUserResponse {
    private String id;
    private String firstName;
    private String lastName;
    private String email;

}
