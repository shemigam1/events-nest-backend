package group.moniepoint.eventsnestserver.notifications.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.notifications.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User-scoped notification feed")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthService authService;

    @Operation(summary = "List the calling user's notifications, newest first",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<?> getMyNotifications(Principal principal,
                                                @PageableDefault(size = 20) Pageable pageable) {
        User user = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(notificationService.getMyNotifications(user.getId(), pageable));
    }

    @Operation(summary = "Mark a notification as read",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable UUID id, Principal principal) {
        User user = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(notificationService.markAsRead(id, user.getId()));
    }
}
