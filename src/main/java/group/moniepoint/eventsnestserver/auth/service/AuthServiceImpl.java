package group.moniepoint.eventsnestserver.auth.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import group.moniepoint.eventsnestserver.auth.dto.ForgotPasswordRequest;
import group.moniepoint.eventsnestserver.auth.dto.LoginRequest;
import group.moniepoint.eventsnestserver.auth.dto.LoginResponse;
import group.moniepoint.eventsnestserver.auth.dto.RegisterRequest;
import group.moniepoint.eventsnestserver.auth.dto.RegisterResponse;
import group.moniepoint.eventsnestserver.auth.dto.ResetPasswordRequest;
import group.moniepoint.eventsnestserver.auth.model.PasswordResetToken;
import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.audit.AuditAction;
import group.moniepoint.eventsnestserver.audit.AuditEntityType;
import group.moniepoint.eventsnestserver.audit.event.AuditEvent;
import group.moniepoint.eventsnestserver.audit.publisher.AuditEventPublisher;
import group.moniepoint.eventsnestserver.auth.repository.PasswordResetTokenRepository;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.email.EmailOutbox;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.security.service.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int TOKEN_EXPIRY_HOURS = 1;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;
    private final AuditEventPublisher auditEventPublisher;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailOutbox emailOutbox;

    @Override
    public EventsNestResponse<RegisterResponse> register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EventsNestException("email already in use");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        User saved = userRepository.save(user);

        RegisterResponse data = RegisterResponse.builder()
                .id(saved.getId())
                .firstName(saved.getFirstName())
                .lastName(saved.getLastName())
                .email(saved.getEmail())
                .role(saved.getRole().name())
                .build();

        EventsNestResponse<RegisterResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("registration successful");
        response.setData(data);
        return response;
    }

    @Override
    public EventsNestResponse<LoginResponse> login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (BadCredentialsException e) {
            auditEventPublisher.publish(AuditEvent.of(
                    null, null,
                    AuditAction.LOGIN_FAILURE, AuditEntityType.USER,
                    request.getEmail(),
                    Map.of("reason", "bad credentials")));
            throw e;
        }

        LoginResponse data = LoginResponse.builder()
                .accessToken(jwtService.generateAccessToken(authentication))
                .refreshToken(jwtService.generateRefreshToken(authentication))
                .build();

        EventsNestResponse<LoginResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("login successful");
        response.setData(data);
        return response;
    }

    @Override
    @Cacheable(value = "user-by-email", key = "#email")
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
    }

    /** Called by AdminServiceImpl when a user's enabled/role status changes. */
    @CacheEvict(value = "user-by-email", key = "#email")
    public void evictUserCache(String email) {
        // eviction only — no body needed
    }

    @Override
    @Transactional
    public EventsNestResponse<Void> forgotPassword(ForgotPasswordRequest request) {
        // Always return the same message regardless of whether email exists (prevents user enumeration)
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String rawToken = generateToken();

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(rawToken)
                    .userId(user.getId())
                    .expiresAt(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS))
                    .build();
            passwordResetTokenRepository.save(resetToken);

            String fullName = buildFullName(user);
            emailOutbox.enqueuePasswordReset(user.getEmail(), fullName, rawToken);
        }

        EventsNestResponse<Void> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("If that email is registered, a reset link has been sent");
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<Void> resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByToken(request.getToken())
                .orElseThrow(() -> new EventsNestException("invalid or expired reset token"));

        if (resetToken.isUsed()) {
            throw new EventsNestException("reset token has already been used");
        }
        if (resetToken.isExpired()) {
            throw new EventsNestException("reset token has expired");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        evictUserCache(user.getEmail());

        EventsNestResponse<Void> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("password reset successfully");
        return response;
    }

    @Override
    public EventsNestResponse<Void> validateResetToken(String token) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByToken(token)
                .orElseThrow(() -> new EventsNestException("invalid or expired reset token"));

        if (resetToken.isUsed()) {
            throw new EventsNestException("reset token has already been used");
        }
        if (resetToken.isExpired()) {
            throw new EventsNestException("reset token has expired");
        }

        EventsNestResponse<Void> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("token is valid");
        return response;
    }

    @Override
    public EventsNestResponse<LoginResponse> refresh(String refreshToken) {
        DecodedJWT decoded = jwtService.validateRefreshToken(refreshToken);
        String email = decoded.getSubject();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("account is disabled");
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));

        LoginResponse data = LoginResponse.builder()
                .accessToken(jwtService.generateAccessToken(authentication))
                .refreshToken(jwtService.generateRefreshToken(authentication))
                .build();

        EventsNestResponse<LoginResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("token refreshed");
        response.setData(data);
        return response;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String buildFullName(User user) {
        StringBuilder sb = new StringBuilder();
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) sb.append(user.getFirstName().trim());
        if (user.getLastName() != null && !user.getLastName().isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(user.getLastName().trim());
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
