package group.moniepoint.eventsnestserver.events.controllers;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.events.service.EventImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}")
@AllArgsConstructor
@Tag(name = "Event Images", description = "Cover image uploads. JPEG/PNG, max 5MB.")
public class EventImageController {

    private final EventImageService eventImageService;
    private final AuthService authService;

    @Operation(
            summary = "Upload or replace the event cover image (organiser only). Required before submitting for approval.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/cover-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCover(@PathVariable UUID eventId,
                                         @RequestParam("file") MultipartFile file,
                                         Principal principal) {
        User uploader = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(eventImageService.uploadCover(eventId, file, uploader));
    }
}
