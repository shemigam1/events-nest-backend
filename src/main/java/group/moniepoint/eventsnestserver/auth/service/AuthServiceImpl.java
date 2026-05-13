package group.moniepoint.eventsnestserver.auth.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import group.moniepoint.eventsnestserver.auth.dto.LoginRequest;
import group.moniepoint.eventsnestserver.auth.dto.LoginResponse;
import group.moniepoint.eventsnestserver.auth.dto.RegisterRequest;
import group.moniepoint.eventsnestserver.auth.dto.RegisterResponse;
import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.audit.AuditAction;
import group.moniepoint.eventsnestserver.audit.AuditEntityType;
import group.moniepoint.eventsnestserver.audit.event.AuditEvent;
import group.moniepoint.eventsnestserver.audit.publisher.AuditEventPublisher;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.security.service.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;
    private final AuditEventPublisher auditEventPublisher;

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
}
