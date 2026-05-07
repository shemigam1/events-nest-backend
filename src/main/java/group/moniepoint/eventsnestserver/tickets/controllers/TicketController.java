package group.moniepoint.eventsnestserver.tickets.controllers;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.tickets.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@AllArgsConstructor
@Tag(name = "Tickets", description = "User-scoped ticket queries")
public class TicketController {

    private final TicketService ticketService;
    private final AuthService authService;

    @Operation(summary = "Get all tickets for the calling user",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/me/tickets")
    public ResponseEntity<?> getMyTickets(Principal principal) {
        User currentUser = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(ticketService.getMyTickets(currentUser));
    }
}
