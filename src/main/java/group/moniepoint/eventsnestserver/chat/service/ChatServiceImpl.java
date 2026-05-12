package group.moniepoint.eventsnestserver.chat.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.chat.dto.request.CreateConversationRequest;
import group.moniepoint.eventsnestserver.chat.dto.request.SendMessageRequest;
import group.moniepoint.eventsnestserver.chat.dto.response.ConversationResponse;
import group.moniepoint.eventsnestserver.chat.dto.response.MessageResponse;
import group.moniepoint.eventsnestserver.chat.model.*;
import group.moniepoint.eventsnestserver.chat.repository.ConversationParticipantRepository;
import group.moniepoint.eventsnestserver.chat.repository.ConversationRepository;
import group.moniepoint.eventsnestserver.chat.repository.MessageRepository;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final EventRespository eventRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public ConversationResponse createOrGetConversation(CreateConversationRequest request, User caller) {
        List<String> participantIds = request.getParticipantIds();

        // DIRECT: exactly one other participant — reuse existing thread if it exists
        if (participantIds.size() == 1 && request.getEventId() == null) {
            String otherId = participantIds.get(0);
            return conversationRepository
                    .findDirectBetween(ConversationType.DIRECT, caller.getId(), otherId)
                    .map(ConversationResponse::from)
                    .orElseGet(() -> ConversationResponse.from(
                            createNew(request, caller, ConversationType.DIRECT)));
        }

        // GROUP: always create a new conversation
        return ConversationResponse.from(createNew(request, caller, ConversationType.GROUP));
    }

    private Conversation createNew(CreateConversationRequest request, User caller, ConversationType type) {
        Events event = null;
        if (request.getEventId() != null) {
            event = eventRepository.findById(request.getEventId())
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        }

        Conversation conversation = Conversation.builder()
                .title(request.getTitle())
                .event(event)
                .type(type)
                .createdBy(caller)
                .participants(new ArrayList<>())
                .build();
        conversationRepository.save(conversation);

        // Add caller
        addParticipant(conversation, caller);

        // Add requested participants
        for (String userId : request.getParticipantIds()) {
            if (userId.equals(caller.getId())) continue;
            User participant = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
            addParticipant(conversation, participant);
        }

        return conversationRepository.save(conversation);
    }

    private void addParticipant(Conversation conversation, User user) {
        ConversationParticipant cp = ConversationParticipant.builder()
                .conversation(conversation)
                .user(user)
                .build();
        conversation.getParticipants().add(cp);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> listMyConversations(User caller) {
        return conversationRepository.findAllByParticipantId(caller.getId())
                .stream()
                .map(ConversationResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getHistory(UUID conversationId, UUID beforeId, int limit, User caller) {
        assertParticipant(conversationId, caller);

        int pageSize = Math.min(limit <= 0 ? 20 : limit, MAX_PAGE_SIZE);
        List<Message> messages;

        if (beforeId != null) {
            messages = messageRepository.findBeforeMessage(
                    conversationId, beforeId, PageRequest.of(0, pageSize));
        } else {
            messages = messageRepository.findLatestByConversationId(
                    conversationId, PageRequest.of(0, pageSize));
        }

        // Return in chronological order (oldest first) for rendering
        return messages.reversed().stream()
                .map(MessageResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(SendMessageRequest request, User caller) {
        UUID conversationId = request.getConversationId();
        assertParticipant(conversationId, caller);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        MessageType type = MessageType.TEXT;
        if (request.getMessageType() != null) {
            try { type = MessageType.valueOf(request.getMessageType().toUpperCase()); }
            catch (IllegalArgumentException ignored) { /* default to TEXT */ }
        }

        Message message = Message.builder()
                .conversation(conversation)
                .sender(caller)
                .content(request.getContent())
                .messageType(type)
                .build();

        messageRepository.save(message);

        // Bump conversation's updatedAt so it floats to the top of conversation lists
        conversation.setUpdatedAt(java.time.LocalDateTime.now());
        conversationRepository.save(conversation);

        MessageResponse response = MessageResponse.from(message);

        // Push to all subscribers of this conversation's topic
        messagingTemplate.convertAndSend(
                "/topic/conversation." + conversationId, response);

        log.debug("Message sent in conversation {} by {}", conversationId, caller.getId());
        return response;
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void assertParticipant(UUID conversationId, User caller) {
        if (!conversationRepository.isParticipant(conversationId, caller.getId())) {
            throw new group.moniepoint.eventsnestserver.exception.UnauthorizedException(
                    "You are not a participant of this conversation");
        }
    }
}
