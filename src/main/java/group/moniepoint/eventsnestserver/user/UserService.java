package group.moniepoint.eventsnestserver.user;

import group.moniepoint.eventsnestserver.dto.EventsNestResponse;
import group.moniepoint.eventsnestserver.user.dto.RegisterUserRequest;
import group.moniepoint.eventsnestserver.user.dto.RegisterUserResponse;
import group.moniepoint.eventsnestserver.user.dto.UserResponse;

public interface UserService {
    EventsNestResponse<?> register(RegisterUserRequest registerUserRequest);


    UserResponse getUserBy(String email);
}
