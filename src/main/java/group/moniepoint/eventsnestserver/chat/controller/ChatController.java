package group.moniepoint.eventsnestserver.chat.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.chat.dto.request.CreateConversationRequest;
import group.moniepoint.eventsnestserver.chat.dto.response.ConversationResponse;
import group.moniepoint.eventsnestserver.chat.dto.response.MessageResponse;
import group.moniepoint.eventsnestserver.chat.service.ChatService;
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
 * REST endpoints for conversation management and message history.
 *
 * Real-time message delivery is handled over WebSocket/STOMP — see ChatWsController.
 * Clients subscribe to /topic/conversation.{id} to receive live messages.
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Conversations and message history")
public class ChatController {

    private final ChatService chatService;
    private final AuthService authService;

    @Operation(summary = "Start or retrieve a conversation",
               description = "Creates a new conversation or returns an existing DIRECT thread between the same two users.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> createOrGet(
            @Valid @RequestBody CreateConversationRequest request,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(chatService.createOrGetConversation(request, caller));
    }

    @Operation(summary = "List my conversations",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationResponse>> list(Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(chatService.listMyConversations(caller));
    }

    @Operation(summary = "Load message history (cursor-based)",
               description = "Returns up to `limit` messages (max 50). Pass `beforeId` to page backwards.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<MessageResponse>> history(
            @PathVariable UUID conversationId,
            @RequestParam(required = false) UUID beforeId,
            @RequestParam(defaultValue = "20") int limit,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(chatService.getHistory(conversationId, beforeId, limit, caller));
    }

    @Operation(summary = "Mark a conversation as read",
               description = "Records the current timestamp as last-read for the calling user. " +
                             "The next GET /conversations response will show unreadCount=0 for this conversation.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable UUID conversationId,
            Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        chatService.markRead(conversationId, caller);
        return ResponseEntity.noContent().build();
    }
}
