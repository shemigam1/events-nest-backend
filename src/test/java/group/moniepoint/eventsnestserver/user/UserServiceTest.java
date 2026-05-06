package group.moniepoint.eventsnestserver.user;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.user.dto.RegisterUserRequest;
import group.moniepoint.eventsnestserver.user.dto.RegisterUserResponse;
import group.moniepoint.eventsnestserver.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(new ModelMapper(), userRepository, passwordEncoder);
    }

    @Test
    void registerPersistsMappedUserWithEncodedPasswordAndDefaultAuthority() {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setEmail("ada@example.com");
        request.setPassword("rawPassword");

        when(passwordEncoder.encode("rawPassword")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId("generated-id");
            return saved;
        });

        EventsNestResponse<RegisterUserResponse> response = userService.register(request);

        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedCaptor.capture());
        User saved = savedCaptor.getValue();

        assertThat(saved.getFirstName()).isEqualTo("Ada");
        assertThat(saved.getLastName()).isEqualTo("Lovelace");
        assertThat(saved.getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getPassword()).isEqualTo("encoded");
        assertThat(saved.getAuthorities()).containsExactly("ATTENDEE");

        verify(passwordEncoder).encode("rawPassword");
        verify(userRepository, times(1)).save(any(User.class));

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getId()).isEqualTo("generated-id");
        assertThat(response.getData().getEmail()).isEqualTo("ada@example.com");
        assertThat(response.getData().getFirstName()).isEqualTo("Ada");
        assertThat(response.getData().getLastName()).isEqualTo("Lovelace");
    }

    @Test
    void registerPropagatesRepositoryFailure() {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setEmail("dup@example.com");
        request.setPassword("rawPassword");

        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("duplicate email");
    }

    @Test
    void getUserByReturnsMappedResponseWhenUserExists() {
        User user = new User();
        user.setId("123");
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setEmail("ada@example.com");
        user.setPassword("encoded");
        user.setAuthorities(List.of("ATTENDEE"));

        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        UserResponse result = userService.getUserBy("ada@example.com");

        assertThat(result.getEmail()).isEqualTo("ada@example.com");
        assertThat(result.getFirstName()).isEqualTo("Ada");
        assertThat(result.getLastName()).isEqualTo("Lovelace");
        assertThat(result.getPassword()).isEqualTo("encoded");
        assertThat(result.getAuthorities()).containsExactly("ATTENDEE");
    }

    @Test
    void getUserByThrowsResourceNotFoundWhenEmailMissing() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserBy("missing@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("user not found");
    }
}
