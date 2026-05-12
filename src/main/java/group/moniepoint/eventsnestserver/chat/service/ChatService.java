package group.moniepoint.eventsnestserver.chat.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.chat.dto.request.CreateConversationRequest;
import group.moniepoint.eventsnestserver.chat.dto.request.SendMessageRequest;
import group.moniepoint.eventsnestserver.chat.dto.response.ConversationResponse;
import group.moniepoint.eventsnestserver.chat.dto.response.MessageResponse;

import java.util.List;
import java.util.UUID;

public interface ChatService {

    ConversationResponse createOrGetConversation(CreateConversationRequest request, User caller);

    List<ConversationResponse> listMyConversations(User caller);

    List<MessageResponse> getHistory(UUID conversationId, UUID beforeId, int limit, User caller);

    MessageResponse sendMessage(SendMessageRequest request, User caller);
}
