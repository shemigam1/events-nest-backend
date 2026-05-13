package group.moniepoint.eventsnestserver.auth;

import group.moniepoint.eventsnestserver.auth.dto.RegisterRequest;
import group.moniepoint.eventsnestserver.auth.dto.RegisterResponse;
import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.auth.service.AuthServiceImpl;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import group.moniepoint.eventsnestserver.audit.publisher.AuditEventPublisher;
import group.moniepoint.eventsnestserver.security.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService – register()")
class AuthServiceRegisterTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JWTService jwtService;
    @Mock private AuditEventPublisher auditEventPublisher;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, authenticationManager, jwtService, auditEventPublisher);
    }

    @Test
    @DisplayName("Saves user with encoded password and USER role")
    void register_savesUserWithEncodedPasswordAndAttendeeRole() {
        RegisterRequest request = registerRequest("semil", "omotade", "semil@example.com", "whoami");

        when(userRepository.existsByEmail("semil@example.com")).thenReturn(false);
        when(passwordEncoder.encode("whoami")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getFirstName()).isEqualTo("semil");
        assertThat(saved.getLastName()).isEqualTo("omotade");
        assertThat(saved.getEmail()).isEqualTo("semil@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("Returns success response with saved entity data")
    void register_returnsSuccessResponseWithSavedEntityData() {
        RegisterRequest request = registerRequest("semil", "omotade", "semil@example.com", "whoami");
        String generatedId = "testuser0001";

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> {
            User user = i.getArgument(0);
            user.setId(generatedId);
            return user;
        });

        EventsNestResponse<RegisterResponse> response = authService.register(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("registration successful");
        assertThat(response.getData().getId()).isEqualTo(generatedId);
        assertThat(response.getData().getFirstName()).isEqualTo("semil");
        assertThat(response.getData().getLastName()).isEqualTo("omotade");
        assertThat(response.getData().getEmail()).isEqualTo("semil@example.com");
        assertThat(response.getData().getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Throws EventsNestException when email is already in use")
    void register_throwsEventsNestExceptionWhenEmailAlreadyExists() {
        RegisterRequest request = registerRequest("semil", "omotade", "semil@example.com", "whoami");

        when(userRepository.existsByEmail("semil@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EventsNestException.class)
                .hasMessage("email already in use");
    }

    @Test
    @DisplayName("Does not persist user or encode password when email already exists")
    void register_doesNotSaveWhenEmailAlreadyExists() {
        RegisterRequest request = registerRequest("semil", "omotade", "semil@example.com", "whoami");

        when(userRepository.existsByEmail("semil@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EventsNestException.class);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("Encodes password before persisting the user entity")
    void register_encodesPasswordBeforeSaving() {
        RegisterRequest request = registerRequest("semil", "omotade", "semil@example.com", "whoami");

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("whoami")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.register(request);

        verify(passwordEncoder).encode("whoami");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("whoami");
    }


    private RegisterRequest registerRequest(String first, String last, String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName(first);
        request.setLastName(last);
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
