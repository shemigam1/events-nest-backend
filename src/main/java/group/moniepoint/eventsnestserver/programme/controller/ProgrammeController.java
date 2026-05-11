package group.moniepoint.eventsnestserver.programme.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.programme.dto.request.CreateProgrammeItemRequest;
import group.moniepoint.eventsnestserver.programme.dto.request.UpdateProgrammeItemRequest;
import group.moniepoint.eventsnestserver.programme.service.ProgrammeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/programme")
@AllArgsConstructor
@Tag(name = "Programme", description = "Event agenda / programme management")
public class ProgrammeController {

    private final ProgrammeService programmeService;
    private final AuthService authService;

    @Operation(summary = "Get the programme for an event (public when programme module is enabled)")
    @GetMapping
    public ResponseEntity<?> getItems(@PathVariable UUID eventId) {
        return ResponseEntity.ok(programmeService.getItems(eventId));
    }

    @Operation(summary = "Add a programme item",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ResponseEntity<?> addItem(@PathVariable UUID eventId,
                                     @Valid @RequestBody CreateProgrammeItemRequest request,
                                     Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(programmeService.addItem(eventId, request, organizer));
    }

    @Operation(summary = "Update a programme item",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{itemId}")
    public ResponseEntity<?> updateItem(@PathVariable UUID eventId,
                                        @PathVariable UUID itemId,
                                        @RequestBody UpdateProgrammeItemRequest request,
                                        Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(programmeService.updateItem(eventId, itemId, request, organizer));
    }

    @Operation(summary = "Delete a programme item",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{itemId}")
    public ResponseEntity<?> deleteItem(@PathVariable UUID eventId,
                                        @PathVariable UUID itemId,
                                        Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(programmeService.deleteItem(eventId, itemId, organizer));
    }
}
