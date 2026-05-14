package group.moniepoint.eventsnestserver.vendor.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.vendor.dto.request.CreateInquiryRequest;
import group.moniepoint.eventsnestserver.vendor.service.VendorInquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Vendor Inquiries", description = "Organizers contact vendors; vendors view received inquiries")
public class VendorInquiryController {

    private final VendorInquiryService inquiryService;
    private final AuthService authService;

    @Operation(summary = "Send a vendor inquiry for an event (organizer only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/events/{eventId}/vendor-inquiries")
    public ResponseEntity<?> sendInquiry(@PathVariable UUID eventId,
                                         @Valid @RequestBody CreateInquiryRequest request,
                                         Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inquiryService.sendInquiry(eventId, request, caller));
    }

    @Operation(summary = "List inquiries the organizer sent for an event",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/events/{eventId}/vendor-inquiries")
    public ResponseEntity<?> listSent(@PathVariable UUID eventId, Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(inquiryService.listSentInquiries(eventId, caller));
    }

    @Operation(summary = "List all inquiries received by the calling vendor",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/vendor-inquiries/received")
    public ResponseEntity<?> listReceived(Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(inquiryService.listReceivedInquiries(caller));
    }

    @Operation(summary = "Close an inquiry (organizer only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/v1/vendor-inquiries/{inquiryId}/close")
    public ResponseEntity<?> closeInquiry(@PathVariable UUID inquiryId, Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(inquiryService.closeInquiry(inquiryId, caller));
    }
}
