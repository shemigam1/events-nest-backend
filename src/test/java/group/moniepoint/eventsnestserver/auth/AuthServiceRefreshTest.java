package group.moniepoint.eventsnestserver.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import group.moniepoint.eventsnestserver.auth.dto.LoginResponse;
import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.auth.service.AuthServiceImpl;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService – refresh()")
class AuthServiceRefreshTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JWTService jwtService;
    @Mock private DecodedJWT decodedJWT;
    @Mock private AuditEventPublisher auditEventPublisher;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, authenticationManager, jwtService, auditEventPublisher);
    }

    @Test
    @DisplayName("Returns a new access/refresh token pair on a valid refresh token")
    void refresh_returnsNewTokenPairOnValidToken() {
        when(jwtService.validateRefreshToken("valid.refresh.token")).thenReturn(decodedJWT);
        when(decodedJWT.getSubject()).thenReturn("semil@example.com");
        when(userRepository.findByEmail("semil@example.com")).thenReturn(Optional.of(enabledUser(Role.USER)));
        when(jwtService.generateAccessToken(any())).thenReturn("new.access.token");
        when(jwtService.generateRefreshToken(any())).thenReturn("new.refresh.token");

        EventsNestResponse<LoginResponse> response = authService.refresh("valid.refresh.token");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("token refreshed");
        assertThat(response.getData().getAccessToken()).isEqualTo("new.access.token");
        assertThat(response.getData().getRefreshToken()).isEqualTo("new.refresh.token");
        assertThat(response.getData().getTokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("Builds Authentication from the user's current role in the database")
    void refresh_buildsAuthenticationFromCurrentRoleInDatabase() {
        when(jwtService.validateRefreshToken("valid.refresh.token")).thenReturn(decodedJWT);
        when(decodedJWT.getSubject()).thenReturn("semil@example.com");
        when(userRepository.findByEmail("semil@example.com")).thenReturn(Optional.of(enabledUser(Role.USER)));
        when(jwtService.generateAccessToken(any())).thenReturn("new.access.token");
        when(jwtService.generateRefreshToken(any())).thenReturn("new.refresh.token");

        authService.refresh("valid.refresh.token");

        ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
        verify(jwtService).generateAccessToken(captor.capture());

        assertThat(captor.getValue().getPrincipal()).isEqualTo("semil@example.com");
        assertThat(captor.getValue().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Throws BadCredentialsException on an invalid or expired refresh token")
    void refresh_throwsBadCredentialsExceptionOnInvalidToken() {
        when(jwtService.validateRefreshToken("bad.token"))
                .thenThrow(new BadCredentialsException("invalid or expired token"));

        assertThatThrownBy(() -> authService.refresh("bad.token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("invalid or expired token");

        verify(userRepository, never()).findByEmail(any());
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when user no longer exists in the database")
    void refresh_throwsResourceNotFoundWhenUserNoLongerExists() {
        when(jwtService.validateRefreshToken("valid.refresh.token")).thenReturn(decodedJWT);
        when(decodedJWT.getSubject()).thenReturn("deleted@example.com");
        when(userRepository.findByEmail("deleted@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("valid.refresh.token"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("user not found");

        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("Throws BadCredentialsException when account is disabled")
    void refresh_throwsBadCredentialsExceptionWhenAccountIsDisabled() {
        when(jwtService.validateRefreshToken("valid.refresh.token")).thenReturn(decodedJWT);
        when(decodedJWT.getSubject()).thenReturn("semil@example.com");
        when(userRepository.findByEmail("semil@example.com")).thenReturn(Optional.of(disabledUser()));

        assertThatThrownBy(() -> authService.refresh("valid.refresh.token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("account is disabled");

        verify(jwtService, never()).generateAccessToken(any());
    }


    private User enabledUser(Role role) {
        return User.builder()
                .email("semil@example.com")
                .passwordHash("hashed")
                .role(role)
                .enabled(true)
                .build();
    }

    private User disabledUser() {
        return User.builder()
                .email("semil@example.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .enabled(false)
                .build();
    }
}
