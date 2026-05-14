package group.moniepoint.eventsnestserver.contracts.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.contracts.dto.request.CreateContractRequest;
import group.moniepoint.eventsnestserver.contracts.dto.request.UpdateContractRequest;
import group.moniepoint.eventsnestserver.contracts.service.VendorContractService;
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
@Tag(name = "Vendor Contracts", description = "Create and manage vendor contracts for events")
public class VendorContractController {

    private final VendorContractService contractService;
    private final AuthService authService;

    @Operation(summary = "Create a vendor contract for an event (organizer only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/events/{eventId}/contracts")
    public ResponseEntity<?> createContract(@PathVariable UUID eventId,
                                            @Valid @RequestBody CreateContractRequest request,
                                            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.createContract(eventId, request, caller));
    }

    @Operation(summary = "List all contracts for an event (organizer only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/events/{eventId}/contracts")
    public ResponseEntity<?> listByEvent(@PathVariable UUID eventId, Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(contractService.listByEvent(eventId, caller));
    }

    @Operation(summary = "List contracts where the caller is the vendor",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/contracts/mine")
    public ResponseEntity<?> listMine(Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(contractService.listMine(caller));
    }

    @Operation(summary = "Get a specific contract",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/contracts/{contractId}")
    public ResponseEntity<?> getContract(@PathVariable UUID contractId, Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(contractService.getContract(contractId, caller));
    }

    @Operation(summary = "Update a DRAFT contract (organizer only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/v1/contracts/{contractId}")
    public ResponseEntity<?> updateContract(@PathVariable UUID contractId,
                                            @Valid @RequestBody UpdateContractRequest request,
                                            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(contractService.updateContract(contractId, request, caller));
    }

    @Operation(summary = "Vendor signs the contract — DRAFT → SIGNED",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/v1/contracts/{contractId}/sign")
    public ResponseEntity<?> signContract(@PathVariable UUID contractId, Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(contractService.signContract(contractId, caller));
    }

    @Operation(summary = "Organizer activates the contract — FUNDED → ACTIVE",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/v1/contracts/{contractId}/activate")
    public ResponseEntity<?> activateContract(@PathVariable UUID contractId, Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(contractService.activateContract(contractId, caller));
    }

    @Operation(summary = "Organizer completes the contract — ACTIVE → COMPLETED",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/v1/contracts/{contractId}/complete")
    public ResponseEntity<?> completeContract(@PathVariable UUID contractId, Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(contractService.completeContract(contractId, caller));
    }

    @Operation(summary = "Terminate a contract (organizer or vendor)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/v1/contracts/{contractId}/terminate")
    public ResponseEntity<?> terminateContract(@PathVariable UUID contractId, Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(contractService.terminateContract(contractId, caller));
    }
}
