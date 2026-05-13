package group.moniepoint.eventsnestserver.vendor.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.model.VendorVerificationStatus;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.admin.dto.request.RejectEventRequest;
import group.moniepoint.eventsnestserver.vendor.dto.request.ApplyForVendorVerificationRequest;
import group.moniepoint.eventsnestserver.vendor.dto.request.ApplyAsVendorRequest;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorApplicationResponse;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorVerificationResponse;
import group.moniepoint.eventsnestserver.vendor.service.VendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Vendor Marketplace", description = "Vendor applications — apply, browse, accept/reject")
public class VendorController {

    private final VendorService vendorService;
    private final AuthService authService;

    @Operation(summary = "Apply for platform vendor verification",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/vendor-verification/apply")
    public ResponseEntity<VendorVerificationResponse> applyForVerification(
            @Valid @RequestBody ApplyForVendorVerificationRequest request,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(vendorService.applyForVerification(request, caller));
    }

    @Operation(summary = "Get my platform vendor verification status",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/vendor-verification/me")
    public ResponseEntity<VendorVerificationResponse> myVerification(Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(vendorService.getMyVerification(caller));
    }

    @Operation(summary = "Admin: list platform vendor verification requests",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/admin/vendor-verifications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<VendorVerificationResponse>> listVerificationRequests(
            @RequestParam(defaultValue = "PENDING") VendorVerificationStatus status) {
        return ResponseEntity.ok(vendorService.listVerificationRequests(status));
    }

    @Operation(summary = "Admin: approve a platform vendor verification request",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/v1/admin/vendor-verifications/{userId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VendorVerificationResponse> approveVerification(@PathVariable String userId) {
        return ResponseEntity.ok(vendorService.approveVerification(userId));
    }

    @Operation(summary = "Admin: reject a platform vendor verification request",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/v1/admin/vendor-verifications/{userId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VendorVerificationResponse> rejectVerification(
            @PathVariable String userId,
            @Valid @RequestBody RejectEventRequest request) {
        return ResponseEntity.ok(vendorService.rejectVerification(userId, request.getReason()));
    }

    // ─── Applicant endpoints ──────────────────────────────────────────────────

    @Operation(summary = "Apply as a vendor for an event",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/events/{eventId}/vendor-applications")
    public ResponseEntity<VendorApplicationResponse> apply(
            @PathVariable UUID eventId,
            @Valid @RequestBody ApplyAsVendorRequest request,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(vendorService.apply(eventId, request, caller));
    }

    @Operation(summary = "List my vendor applications",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/vendor-applications/mine")
    public ResponseEntity<List<VendorApplicationResponse>> mine(Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(vendorService.listMyApplications(caller));
    }

    // ─── Organiser / Manager endpoints ───────────────────────────────────────

    @Operation(summary = "Browse vendor applications for an event",
               description = "Optional ?status=PENDING|ACCEPTED|REJECTED filter.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/events/{eventId}/vendor-applications")
    public ResponseEntity<List<VendorApplicationResponse>> list(
            @PathVariable UUID eventId,
            @RequestParam(required = false) String status,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(vendorService.listApplicationsForEvent(eventId, status, caller));
    }

    @Operation(summary = "Accept a vendor application",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/events/{eventId}/vendor-applications/{applicationId}/accept")
    public ResponseEntity<VendorApplicationResponse> accept(
            @PathVariable UUID eventId,
            @PathVariable UUID applicationId,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(vendorService.accept(eventId, applicationId, caller));
    }

    @Operation(summary = "Reject a vendor application",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/events/{eventId}/vendor-applications/{applicationId}/reject")
    public ResponseEntity<VendorApplicationResponse> reject(
            @PathVariable UUID eventId,
            @PathVariable UUID applicationId,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(vendorService.reject(eventId, applicationId, caller));
    }
}
