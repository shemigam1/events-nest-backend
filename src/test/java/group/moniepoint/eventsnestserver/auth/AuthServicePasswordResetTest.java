package group.moniepoint.eventsnestserver.auth;

import group.moniepoint.eventsnestserver.auth.dto.ForgotPasswordRequest;
import group.moniepoint.eventsnestserver.auth.dto.ResetPasswordRequest;
import group.moniepoint.eventsnestserver.auth.model.PasswordResetToken;
import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.PasswordResetTokenRepository;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.auth.service.AuthServiceImpl;
import group.moniepoint.eventsnestserver.audit.publisher.AuditEventPublisher;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.email.EmailOutbox;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.security.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — forgot/reset password")
class AuthServicePasswordResetTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JWTService jwtService;
    @Mock private AuditEventPublisher auditEventPublisher;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private EmailOutbox emailOutbox;

    private AuthServiceImpl authService;

    private User alice;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository, passwordEncoder, authenticationManager,
                jwtService, auditEventPublisher, tokenRepository, emailOutbox);

        alice = User.builder()
                .id("alice0000001")
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@test.com")
                .passwordHash("old-hash")
                .role(Role.USER)
                .build();
    }

    // ─── forgotPassword ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("forgotPassword()")
    class ForgotPassword {

        @Test
        @DisplayName("Creates a token and enqueues an email when the address is registered")
        void createsTokenAndEnqueuesEmail() {
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(alice));
            when(tokenRepository.save(any(PasswordResetToken.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("alice@test.com");

            EventsNestResponse<Void> response = authService.forgotPassword(req);

            assertThat(response.isSuccess()).isTrue();

            ArgumentCaptor<PasswordResetToken> tokenCaptor =
                    ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenRepository).save(tokenCaptor.capture());

            PasswordResetToken saved = tokenCaptor.getValue();
            assertThat(saved.getUserId()).isEqualTo(alice.getId());
            assertThat(saved.getToken()).isNotBlank().hasSize(64); // 32 bytes hex
            assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
            assertThat(saved.isUsed()).isFalse();

            verify(emailOutbox).enqueuePasswordReset(
                    eq("alice@test.com"), anyString(), eq(saved.getToken()));
        }

        @Test
        @DisplayName("Returns the same success message even when the email is not registered")
        void silentlySucceedsForUnknownEmail() {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("ghost@test.com");

            EventsNestResponse<Void> response = authService.forgotPassword(req);

            assertThat(response.isSuccess()).isTrue();
            verify(tokenRepository, never()).save(any());
            verify(emailOutbox, never()).enqueuePasswordReset(any(), any(), any());
        }

        @Test
        @DisplayName("Token expiry is set to 1 hour from now")
        void tokenExpiresInOneHour() {
            when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(alice));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("alice@test.com");

            authService.forgotPassword(req);

            ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenRepository).save(captor.capture());

            LocalDateTime expiry = captor.getValue().getExpiresAt();
            assertThat(expiry).isBetween(
                    LocalDateTime.now().plusMinutes(59),
                    LocalDateTime.now().plusMinutes(61));
        }
    }

    // ─── resetPassword ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resetPassword()")
    class ResetPassword {

        @Test
        @DisplayName("Updates passwordHash and marks token as used on valid request")
        void updatesPasswordAndMarksUsed() {
            PasswordResetToken token = validToken();
            when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
            when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
            when(passwordEncoder.encode("newSecret99")).thenReturn("new-hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("valid-token");
            req.setNewPassword("newSecret99");

            EventsNestResponse<Void> response = authService.resetPassword(req);

            assertThat(response.isSuccess()).isTrue();

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("new-hash");

            ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenRepository).save(tokenCaptor.capture());
            assertThat(tokenCaptor.getValue().isUsed()).isTrue();
        }

        @Test
        @DisplayName("Throws EventsNestException when token does not exist")
        void throwsWhenTokenNotFound() {
            when(tokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("bad-token");
            req.setNewPassword("somePassword1");

            assertThatThrownBy(() -> authService.resetPassword(req))
                    .isInstanceOf(EventsNestException.class)
                    .hasMessageContaining("invalid or expired");
        }

        @Test
        @DisplayName("Throws EventsNestException when token has already been used")
        void throwsWhenTokenAlreadyUsed() {
            PasswordResetToken token = validToken();
            token.setUsed(true);
            when(tokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("used-token");
            req.setNewPassword("somePassword1");

            assertThatThrownBy(() -> authService.resetPassword(req))
                    .isInstanceOf(EventsNestException.class)
                    .hasMessageContaining("already been used");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws EventsNestException when token has expired")
        void throwsWhenTokenExpired() {
            PasswordResetToken token = validToken();
            token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("expired-token");
            req.setNewPassword("somePassword1");

            assertThatThrownBy(() -> authService.resetPassword(req))
                    .isInstanceOf(EventsNestException.class)
                    .hasMessageContaining("expired");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when user is missing for a valid token")
        void throwsWhenUserNotFound() {
            PasswordResetToken token = validToken();
            when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
            when(userRepository.findById(alice.getId())).thenReturn(Optional.empty());

            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("valid-token");
            req.setNewPassword("somePassword1");

            assertThatThrownBy(() -> authService.resetPassword(req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private PasswordResetToken validToken() {
        return PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .token("valid-token")
                .userId(alice.getId())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(false)
                .build();
    }
}
