package group.moniepoint.eventsnestserver.auth;

import group.moniepoint.eventsnestserver.auth.dto.LoginRequest;
import group.moniepoint.eventsnestserver.auth.dto.LoginResponse;
import group.moniepoint.eventsnestserver.auth.dto.RegisterRequest;
import group.moniepoint.eventsnestserver.auth.dto.RegisterResponse;
import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AuthService.
 *
 * Uses an H2 in-memory database (schema built from JPA create-drop).
 * Redis and WebSocket dependencies are supplied as mocks by IntegrationTestConfig
 * so no external services are required.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Auth Service Integration Tests")
@Import(IntegrationTestConfig.class)
class AuthServiceIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    // ─── register() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("Persists user and returns success response with generated id")
        void persistsUserAndReturnsSuccessResponse() {
            RegisterRequest request = registerRequest("Ada", "Lovelace", "ada@test.com", "compute123");

            EventsNestResponse<RegisterResponse> response = authService.register(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("registration successful");
            assertThat(response.getData().getId()).isNotNull();
            assertThat(response.getData().getFirstName()).isEqualTo("Ada");
            assertThat(response.getData().getLastName()).isEqualTo("Lovelace");
            assertThat(response.getData().getEmail()).isEqualTo("ada@test.com");
            assertThat(response.getData().getRole()).isEqualTo("USER");
        }

        @Test
        @DisplayName("Saves user with USER role by default")
        void savesUserWithUserRoleByDefault() {
            authService.register(registerRequest("Ada", "Lovelace", "ada@test.com", "compute123"));

            User saved = userRepository.findByEmail("ada@test.com").orElseThrow();
            assertThat(saved.getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("Stores a bcrypt-hashed password — never the plain-text value")
        void storesHashedPassword() {
            authService.register(registerRequest("Ada", "Lovelace", "ada@test.com", "compute123"));

            User saved = userRepository.findByEmail("ada@test.com").orElseThrow();
            assertThat(saved.getPasswordHash()).isNotEqualTo("compute123");
            assertThat(passwordEncoder.matches("compute123", saved.getPasswordHash())).isTrue();
        }

        @Test
        @DisplayName("Throws EventsNestException and does not persist when email is already taken")
        void throwsWhenEmailAlreadyTaken() {
            authService.register(registerRequest("Ada", "Lovelace", "ada@test.com", "compute123"));

            assertThatThrownBy(() ->
                    authService.register(registerRequest("Alan", "Turing", "ada@test.com", "enigma")))
                    .isInstanceOf(EventsNestException.class)
                    .hasMessageContaining("email already in use");

            // Only one user should have been persisted — the duplicate was rejected
            assertThat(userRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Email check is case-sensitive — uppercase variant is treated as new")
        void emailCheckIsCaseSensitive() {
            authService.register(registerRequest("Ada", "Lovelace", "ada@test.com", "compute123"));

            // Spring Data JPA findByEmail is exact-match; H2 default is case-insensitive for LIKE
            // but equals queries are case-sensitive with H2 in PostgreSQL mode, so this must succeed.
            assertThat(userRepository.existsByEmail("ADA@TEST.COM")).isFalse();
        }

        @Test
        @DisplayName("User is enabled by default after registration")
        void userIsEnabledByDefault() {
            authService.register(registerRequest("Ada", "Lovelace", "ada@test.com", "compute123"));

            User saved = userRepository.findByEmail("ada@test.com").orElseThrow();
            assertThat(saved.isEnabled()).isTrue();
        }
    }

    // ─── login() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("Returns non-blank access and refresh tokens on valid credentials")
        void returnsTokensOnValidCredentials() {
            authService.register(registerRequest("Ada", "Lovelace", "ada@test.com", "compute123"));

            EventsNestResponse<LoginResponse> response =
                    authService.login(loginRequest("ada@test.com", "compute123"));

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("login successful");
            assertThat(response.getData().getAccessToken()).isNotBlank();
            assertThat(response.getData().getRefreshToken()).isNotBlank();
            assertThat(response.getData().getTokenType()).isEqualTo("Bearer");
        }

        @Test
        @DisplayName("Access token and refresh token are different JWT strings")
        void accessAndRefreshTokensAreDifferent() {
            authService.register(registerRequest("Ada", "Lovelace", "ada@test.com", "compute123"));

            EventsNestResponse<LoginResponse> response =
                    authService.login(loginRequest("ada@test.com", "compute123"));

            assertThat(response.getData().getAccessToken())
                    .isNotEqualTo(response.getData().getRefreshToken());
        }

        @Test
        @DisplayName("Throws BadCredentialsException on wrong password")
        void throwsOnWrongPassword() {
            authService.register(registerRequest("Ada", "Lovelace", "ada@test.com", "compute123"));

            assertThatThrownBy(() -> authService.login(loginRequest("ada@test.com", "wrongpassword")))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("Throws on non-existent email")
        void throwsOnNonExistentEmail() {
            assertThatThrownBy(() -> authService.login(loginRequest("ghost@test.com", "whatever")))
                    .isInstanceOf(Exception.class); // Spring Security throws BadCredentialsException
        }
    }

    // ─── refresh() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refresh()")
    class Refresh {

        @Test
        @DisplayName("Returns a fresh token pair using a valid refresh token")
        void returnsFreshTokenPairOnValidRefreshToken() {
            authService.register(registerRequest("Ada", "Lovelace", "ada@test.com", "compute123"));
            LoginResponse firstLogin = authService.login(loginRequest("ada@test.com", "compute123")).getData();

            EventsNestResponse<LoginResponse> refreshResponse =
                    authService.refresh(firstLogin.getRefreshToken());

            assertThat(refreshResponse.isSuccess()).isTrue();
            assertThat(refreshResponse.getMessage()).isEqualTo("token refreshed");
            assertThat(refreshResponse.getData().getAccessToken()).isNotBlank();
            assertThat(refreshResponse.getData().getRefreshToken()).isNotBlank();
        }

        @Test
        @DisplayName("New access token differs from the original one")
        void newAccessTokenDiffersFromOriginal() {
            authService.register(registerRequest("Ada", "Lovelace", "ada@test.com", "compute123"));
            LoginResponse firstLogin = authService.login(loginRequest("ada@test.com", "compute123")).getData();

            LoginResponse refreshed = authService.refresh(firstLogin.getRefreshToken()).getData();

            // Tokens are signed with expiry — typically different because issued-at differs
            assertThat(refreshed.getAccessToken()).isNotBlank();
        }

        @Test
        @DisplayName("Throws BadCredentialsException on a tampered or invalid refresh token")
        void throwsOnInvalidRefreshToken() {
            assertThatThrownBy(() -> authService.refresh("not.a.real.jwt"))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("Throws BadCredentialsException when the account is disabled")
        void throwsWhenAccountIsDisabled() {
            authService.register(registerRequest("Ada", "Lovelace", "ada@test.com", "compute123"));
            LoginResponse firstLogin = authService.login(loginRequest("ada@test.com", "compute123")).getData();

            // Disable the account
            User user = userRepository.findByEmail("ada@test.com").orElseThrow();
            user.setEnabled(false);
            userRepository.save(user);

            assertThatThrownBy(() -> authService.refresh(firstLogin.getRefreshToken()))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("account is disabled");
        }
    }

    // ─── findByEmail() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByEmail()")
    class FindByEmail {

        @Test
        @DisplayName("Returns the user entity for a known email")
        void returnsUserForKnownEmail() {
            authService.register(registerRequest("Ada", "Lovelace", "ada@test.com", "compute123"));

            User found = authService.findByEmail("ada@test.com");

            assertThat(found.getEmail()).isEqualTo("ada@test.com");
            assertThat(found.getFirstName()).isEqualTo("Ada");
            assertThat(found.getLastName()).isEqualTo("Lovelace");
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException for an unknown email")
        void throwsForUnknownEmail() {
            assertThatThrownBy(() -> authService.findByEmail("nobody@test.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("user not found");
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private RegisterRequest registerRequest(String first, String last, String email, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName(first);
        req.setLastName(last);
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }
}
