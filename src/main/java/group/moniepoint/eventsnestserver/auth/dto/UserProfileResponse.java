package group.moniepoint.eventsnestserver.auth.dto;

import group.moniepoint.eventsnestserver.auth.model.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileResponse {
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String role;

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
