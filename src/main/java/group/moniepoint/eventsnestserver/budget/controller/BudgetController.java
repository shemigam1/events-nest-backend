package group.moniepoint.eventsnestserver.budget.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.budget.dto.request.AddLineItemRequest;
import group.moniepoint.eventsnestserver.budget.dto.request.CreateBudgetRequest;
import group.moniepoint.eventsnestserver.budget.dto.request.MarkLineItemPaidRequest;
import group.moniepoint.eventsnestserver.budget.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

/**
 * Budget tracker for event organisers and managers.
 *
 * All endpoints are scoped to /api/v1/events/{eventId}/budget so the event
 * context is always explicit. Both ORGANIZER and MANAGER roles can access
 * the budget — the service layer enforces this.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/budget")
@RequiredArgsConstructor
@Tag(name = "Budget Tracker", description = "Event budget planning, expense tracking, and P&L summary")
public class BudgetController {

    private final BudgetService budgetService;
    private final AuthService authService;

    @Operation(summary = "Create the budget envelope for an event",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ResponseEntity<?> createBudget(@PathVariable UUID eventId,
                                          @Valid @RequestBody CreateBudgetRequest request,
                                          Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(budgetService.createBudget(eventId, request, caller));
    }

    @Operation(summary = "Update budget ceiling or notes",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping
    public ResponseEntity<?> updateBudget(@PathVariable UUID eventId,
                                          @Valid @RequestBody CreateBudgetRequest request,
                                          Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(budgetService.updateBudget(eventId, request, caller));
    }

    @Operation(summary = "Get full budget summary — planned vs actual, revenue, P&L",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<?> getSummary(@PathVariable UUID eventId, Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(budgetService.getSummary(eventId, caller));
    }

    @Operation(summary = "Add a planned expense line item",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/items")
    public ResponseEntity<?> addLineItem(@PathVariable UUID eventId,
                                         @Valid @RequestBody AddLineItemRequest request,
                                         Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(budgetService.addLineItem(eventId, request, caller));
    }

    @Operation(summary = "Mark a planned expense as paid with the actual amount. " +
                         "Fires an 80% budget alert if the threshold is newly crossed.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/items/{itemId}/paid")
    public ResponseEntity<?> markPaid(@PathVariable UUID eventId,
                                      @PathVariable UUID itemId,
                                      @Valid @RequestBody MarkLineItemPaidRequest request,
                                      Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(budgetService.markPaid(eventId, itemId, request, caller));
    }

    @Operation(summary = "Delete a PLANNED (unpaid) line item",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<?> deleteLineItem(@PathVariable UUID eventId,
                                            @PathVariable UUID itemId,
                                            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(budgetService.deleteLineItem(eventId, itemId, caller));
    }
}
