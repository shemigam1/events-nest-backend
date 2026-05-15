package group.moniepoint.eventsnestserver.auth.controller;

import group.moniepoint.eventsnestserver.auth.dto.ChangePasswordRequest;
import group.moniepoint.eventsnestserver.auth.dto.UpdateProfileRequest;
import group.moniepoint.eventsnestserver.auth.dto.UserProfileResponse;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Me", description = "Current user profile management")
public class MeController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "Get current user profile")
    @GetMapping
    public ResponseEntity<EventsNestResponse<UserProfileResponse>> getProfile(Principal principal) {
        User user = authService.findByEmail(principal.getName());

        EventsNestResponse<UserProfileResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("profile retrieved");
        response.setData(UserProfileResponse.from(user));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update current user's first and last name")
    @PatchMapping
    public ResponseEntity<EventsNestResponse<UserProfileResponse>> updateProfile(
            @RequestBody @Valid UpdateProfileRequest request,
            Principal principal) {
        User user = authService.findByEmail(principal.getName());

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        userRepository.save(user);

        EventsNestResponse<UserProfileResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("profile updated");
        response.setData(UserProfileResponse.from(user));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Change current user's password")
    @PostMapping("/change-password")
    public ResponseEntity<EventsNestResponse<Void>> changePassword(
            @RequestBody @Valid ChangePasswordRequest request,
            Principal principal) {
        User user = authService.findByEmail(principal.getName());

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new EventsNestException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        EventsNestResponse<Void> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("password changed successfully");
        return ResponseEntity.ok(response);
    }
}
