package group.moniepoint.eventsnestserver.chat.ws;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.chat.dto.request.SendMessageRequest;
import group.moniepoint.eventsnestserver.chat.dto.response.MessageResponse;
import group.moniepoint.eventsnestserver.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP message handler for real-time chat.
 *
 * Client flow:
 *   1. Connect:    STOMP CONNECT  ws://host/ws  (Authorization: Bearer <token>)
 *   2. Subscribe:  /topic/conversation.{conversationId}
 *   3. Send:       /app/chat.send  { conversationId, content }
 *   4. Receive:    MessageResponse broadcast to /topic/conversation.{id}
 *
 * The REST layer (ChatController) handles history loading and conversation creation.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWsController {

    private final ChatService chatService;
    private final AuthService authService;

    /**
     * Receives a message from a client, persists it, and broadcasts it to
     * all subscribers of /topic/conversation.{conversationId}.
     */
    @MessageMapping("/chat.send")
    public MessageResponse send(@Payload @Valid SendMessageRequest request,
                                Principal principal) {
        User caller = authService.findByEmail(principal.getName());
        return chatService.sendMessage(request, caller);
        // SimpMessagingTemplate.convertAndSend(...) inside ChatServiceImpl
        // handles the broadcast — no @SendTo needed here.
    }
}
