package group.moniepoint.eventsnestserver.user;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.user.dto.RegisterUserRequest;
import group.moniepoint.eventsnestserver.user.dto.RegisterUserResponse;
import group.moniepoint.eventsnestserver.user.dto.UserResponse;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {
    private final ModelMapper modelMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public EventsNestResponse<RegisterUserResponse> register(RegisterUserRequest registerUserRequest) {
        User user = modelMapper.map(registerUserRequest, User.class);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setAuthorities(List.of("ATTENDEE"));

        RegisterUserResponse registerUserResponse = modelMapper.map(userRepository.save(user), RegisterUserResponse.class);
        EventsNestResponse<RegisterUserResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setData(registerUserResponse);
        response.setMessage("User created sucessfully");

        return response;
    }

    @Override
    public UserResponse getUserBy(String email) {
        return modelMapper.map(findByEmail(email), UserResponse.class);
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
    }
}
