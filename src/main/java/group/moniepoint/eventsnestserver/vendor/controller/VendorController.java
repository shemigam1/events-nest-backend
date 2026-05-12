package group.moniepoint.eventsnestserver.vendor.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.vendor.dto.request.ApplyAsVendorRequest;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorApplicationResponse;
import group.moniepoint.eventsnestserver.vendor.service.VendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
