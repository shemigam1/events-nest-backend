package group.moniepoint.eventsnestserver.contracts.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.contracts.dto.request.AddMilestoneRequest;
import group.moniepoint.eventsnestserver.contracts.service.EscrowService;
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
@RequestMapping("/api/v1/contracts/{contractId}/escrow")
@RequiredArgsConstructor
@Tag(name = "Escrow", description = "Manage escrow accounts and milestone-based fund releases")
public class EscrowController {

    private final EscrowService escrowService;
    private final AuthService authService;

    @Operation(summary = "Fund the escrow for a signed contract (organizer only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/fund")
    public ResponseEntity<?> fundEscrow(@PathVariable UUID contractId, Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(escrowService.fundEscrow(contractId, caller));
    }

    @Operation(summary = "Get the escrow account for a contract",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<?> getEscrow(@PathVariable UUID contractId, Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(escrowService.getEscrow(contractId, caller));
    }

    @Operation(summary = "Add a milestone to the escrow (organizer only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/milestones")
    public ResponseEntity<?> addMilestone(@PathVariable UUID contractId,
                                          @Valid @RequestBody AddMilestoneRequest request,
                                          Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(escrowService.addMilestone(contractId, request, caller));
    }

    @Operation(summary = "Approve a completed milestone — PENDING → APPROVED (organizer only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/milestones/{milestoneId}/approve")
    public ResponseEntity<?> approveMilestone(@PathVariable UUID contractId,
                                              @PathVariable UUID milestoneId,
                                              Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(escrowService.approveMilestone(contractId, milestoneId, caller));
    }

    @Operation(summary = "Release funds for an approved milestone — APPROVED → RELEASED (organizer only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/milestones/{milestoneId}/release")
    public ResponseEntity<?> releaseMilestone(@PathVariable UUID contractId,
                                              @PathVariable UUID milestoneId,
                                              Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(escrowService.releaseMilestone(contractId, milestoneId, caller));
    }
}
