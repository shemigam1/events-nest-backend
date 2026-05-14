package group.moniepoint.eventsnestserver.comments.controller;

import group.moniepoint.eventsnestserver.admin.dto.response.PageResponse;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.comments.dto.request.CreateCommentRequest;
import group.moniepoint.eventsnestserver.comments.dto.request.UpdateCommentRequest;
import group.moniepoint.eventsnestserver.comments.dto.response.CommentResponse;
import group.moniepoint.eventsnestserver.comments.service.CommentService;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
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

/**
 * Social comments on events. The /events/{id}/comments routes scope writes
 * + listing to a specific event; per-comment routes (edit, delete, reply
 * listing, like toggle) live under /comments/{id} so the URL is shorter
 * for things keyed by comment id alone.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Social comments + reactions on events")
public class CommentController {

    private final CommentService commentService;
    private final AuthService    authService;

    @Operation(summary = "Post a comment on an event",
               description = "Set parentId to make this a reply. Replies are one level deep.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/events/{eventId}/comments")
    public ResponseEntity<EventsNestResponse<CommentResponse>> create(
            @PathVariable UUID eventId,
            @Valid @RequestBody CreateCommentRequest request,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(commentService.create(eventId, request, caller));
    }

    @Operation(summary = "List top-level comments on an event (paged, newest first)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/events/{eventId}/comments")
    public ResponseEntity<PageResponse<CommentResponse>> list(
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(commentService.listTopLevel(eventId, page, size, caller));
    }

    @Operation(summary = "List replies under a comment (oldest first)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/comments/{commentId}/replies")
    public ResponseEntity<List<CommentResponse>> replies(
            @PathVariable UUID commentId,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(commentService.listReplies(commentId, caller));
    }

    @Operation(summary = "Edit your own comment",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/v1/comments/{commentId}")
    public ResponseEntity<EventsNestResponse<CommentResponse>> update(
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(commentService.update(commentId, request, caller));
    }

    @Operation(summary = "Soft-delete a comment (own comment, or any if you're the organiser/manager)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/api/v1/comments/{commentId}")
    public ResponseEntity<EventsNestResponse<Void>> delete(
            @PathVariable UUID commentId,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(commentService.delete(commentId, caller));
    }

    @Operation(summary = "Toggle like on a comment — idempotent",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/comments/{commentId}/like")
    public ResponseEntity<EventsNestResponse<CommentResponse>> toggleLike(
            @PathVariable UUID commentId,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(commentService.toggleLike(commentId, caller));
    }
}
