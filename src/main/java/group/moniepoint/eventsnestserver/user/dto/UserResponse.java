package group.moniepoint.eventsnestserver.user.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserResponse {
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private List<String> authorities;
}
