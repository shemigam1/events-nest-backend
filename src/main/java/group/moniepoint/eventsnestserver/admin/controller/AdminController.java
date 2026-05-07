package group.moniepoint.eventsnestserver.admin.controller;

import group.moniepoint.eventsnestserver.admin.dto.request.CompleteAdminInvitationRequest;
import group.moniepoint.eventsnestserver.admin.dto.request.InviteAdminRequest;
import group.moniepoint.eventsnestserver.admin.dto.request.RejectEventRequest;
import group.moniepoint.eventsnestserver.admin.service.AdminService;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@AllArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Platform-wide moderation and analytics (ADMIN only)")
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "List events filtered by status",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/events")
    public ResponseEntity<?> getEvents(
            @RequestParam(defaultValue = "PENDING_APPROVAL") EventStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getEventsByStatus(status, pageable));
    }

    @Operation(summary = "Approve a pending event (status → PUBLISHED)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/events/{id}/approve")
    public ResponseEntity<?> approveEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.approveEvent(id));
    }

    @Operation(summary = "Reject a pending event with a reason (status → DRAFT)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/events/{id}/reject")
    public ResponseEntity<?> rejectEvent(@PathVariable UUID id,
                                         @Valid @RequestBody RejectEventRequest request) {
        return ResponseEntity.ok(adminService.rejectEvent(id, request));
    }

    @Operation(summary = "Paginated list of all platform users",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/users")
    public ResponseEntity<?> getUsers(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getUsers(pageable));
    }

    @Operation(summary = "Platform-wide analytics snapshot",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics() {
        return ResponseEntity.ok(adminService.getAnalytics());
    }

    @Operation(summary = "Send an admin invitation email to a new address",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/invite")
    public ResponseEntity<?> inviteAdmin(@Valid @RequestBody InviteAdminRequest request) {
        return ResponseEntity.ok(adminService.inviteAdmin(request));
    }

    @Operation(summary = "Complete admin registration via invitation token (public)")
    @PostMapping("/invite/complete")
    public ResponseEntity<?> completeInvitation(@Valid @RequestBody CompleteAdminInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.completeAdminInvitation(request));
    }
}
