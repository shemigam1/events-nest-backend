package group.moniepoint.eventsnestserver.auth;

import group.moniepoint.eventsnestserver.auth.dto.LoginRequest;
import group.moniepoint.eventsnestserver.auth.dto.LoginResponse;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.auth.service.AuthServiceImpl;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.security.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, authenticationManager, jwtService);
    }

    @Test
    void login_returnsAccessAndRefreshTokensOnValidCredentials() {
        LoginRequest request = loginRequest("semil@example.com", "secret");
        Authentication authenticated = authenticatedToken("semil@example.com", "ROLE_ATTENDEE");

        when(authenticationManager.authenticate(any())).thenReturn(authenticated);
        when(jwtService.generateAccessToken(authenticated)).thenReturn("access.token.value");
        when(jwtService.generateRefreshToken(authenticated)).thenReturn("refresh.token.value");

        EventsNestResponse<LoginResponse> response = authService.login(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("login successful");
        assertThat(response.getData().getAccessToken()).isEqualTo("access.token.value");
        assertThat(response.getData().getRefreshToken()).isEqualTo("refresh.token.value");
        assertThat(response.getData().getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_passesEmailAndPasswordToAuthenticationManager() {
        LoginRequest request = loginRequest("semil@example.com", "secret");
        Authentication authenticated = authenticatedToken("semil@example.com", "ROLE_ATTENDEE");

        when(authenticationManager.authenticate(any())).thenReturn(authenticated);
        when(jwtService.generateAccessToken(any())).thenReturn("access.token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh.token");

        authService.login(request);

        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());

        assertThat(captor.getValue().getPrincipal()).isEqualTo("semil@example.com");
        assertThat(captor.getValue().getCredentials()).isEqualTo("secret");
    }

    @Test
    void login_throwsBadCredentialsExceptionOnWrongPassword() {
        LoginRequest request = loginRequest("semil@example.com", "wrongpassword");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("invalid credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("invalid credentials");

        verify(jwtService, never()).generateAccessToken(any());
        verify(jwtService, never()).generateRefreshToken(any());
    }

    @Test
    void login_throwsDisabledExceptionWhenAccountIsDisabled() {
        LoginRequest request = loginRequest("semil@example.com", "secret");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("account is disabled"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(DisabledException.class)
                .hasMessage("account is disabled");

        verify(jwtService, never()).generateAccessToken(any());
        verify(jwtService, never()).generateRefreshToken(any());
    }

    @Test
    void login_throwsBadCredentialsExceptionWhenOrganiserNotApproved() {
        LoginRequest request = loginRequest("organizer@example.com", "secret");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("organiser account pending admin approval"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("organiser account pending admin approval");

        verify(jwtService, never()).generateAccessToken(any());
        verify(jwtService, never()).generateRefreshToken(any());
    }

    @Test
    void login_passesAuthenticatedObjectToJwtService() {
        LoginRequest request = loginRequest("semil@example.com", "secret");
        Authentication authenticated = authenticatedToken("ada@example.com", "ROLE_ADMIN");

        when(authenticationManager.authenticate(any())).thenReturn(authenticated);
        when(jwtService.generateAccessToken(authenticated)).thenReturn("access.token");
        when(jwtService.generateRefreshToken(authenticated)).thenReturn("refresh.token");

        authService.login(request);

        verify(jwtService).generateAccessToken(authenticated);
        verify(jwtService).generateRefreshToken(authenticated);
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private Authentication authenticatedToken(String email, String role) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority(role)));
    }
}
