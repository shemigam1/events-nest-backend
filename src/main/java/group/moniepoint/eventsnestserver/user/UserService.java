package group.moniepoint.eventsnestserver.user;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.user.dto.RegisterUserRequest;
import group.moniepoint.eventsnestserver.user.dto.UserResponse;

public interface UserService {
    EventsNestResponse<?> register(RegisterUserRequest registerUserRequest);

    UserResponse getUserBy(String email);

    User findByEmail(String email);
}
