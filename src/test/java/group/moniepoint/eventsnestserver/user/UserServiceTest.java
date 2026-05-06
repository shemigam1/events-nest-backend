package group.moniepoint.eventsnestserver.user;

import group.moniepoint.eventsnestserver.dto.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.user.dto.RegisterUserRequest;
import group.moniepoint.eventsnestserver.user.dto.RegisterUserResponse;
import group.moniepoint.eventsnestserver.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void testCanRegisterUser(){
        RegisterUserRequest registerUserRequest = new RegisterUserRequest();
        registerUserRequest.setPassword("rawPassword");
        User user = new User();
        user.setId("123");
        user.setPassword("rawPassword");

        RegisterUserResponse registerUserResponse = new RegisterUserResponse();
        registerUserResponse.setId(user.getId());

        when(modelMapper.map(registerUserRequest, User.class)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(passwordEncoder.encode(any(String.class))).thenReturn("12345");
        when(modelMapper.map(user, RegisterUserResponse.class)).thenReturn(registerUserResponse);

        EventsNestResponse<RegisterUserResponse> response = userService.register(registerUserRequest);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response).isNotNull();
        assertThat(response.getData()).isNotNull();

        verify(passwordEncoder).encode("rawPassword");
        verify(userRepository).save(argThat(u ->
                u.getPassword().equals("12345") &&
                        u.getAuthorities().equals(List.of("ATTENDEE"))));
    }

    @Test
    void getUserByReturnsMappedResponse() {
        User user = new User();
        user.setEmail("ada@x.com");
        UserResponse mapped = new UserResponse();
        mapped.setEmail("ada@x.com");

        when(userRepository.findByEmail("ada@x.com")).thenReturn(Optional.of(user));
        when(modelMapper.map(user, UserResponse.class)).thenReturn(mapped);

        UserResponse result = userService.getUserBy("ada@x.com");

        assertThat(result.getEmail()).isEqualTo("ada@x.com");
    }

    @Test
    void getUserByThrowsWhenEmailMissing() {
        when(userRepository.findByEmail("missing@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserBy("missing@x.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("user not found");
    }


    @Test
    void testCanLoginUser(){

    }


}