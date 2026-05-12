package group.moniepoint.eventsnestserver.chat;

import group.moniepoint.eventsnestserver.auth.model.Role;
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
import group.moniepoint.eventsnestserver.chat.service.ChatServiceImpl;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatServiceImpl.
 *
 * All dependencies are mocked — no Spring context, no database.
 * Exercises the service logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService Unit Tests")
class ChatServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationParticipantRepository participantRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventRespository eventRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private ChatServiceImpl chatService;

    private User alice;
    private User bob;
    private User charlie;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(
                conversationRepository,
                participantRepository,
                messageRepository,
                userRepository,
                eventRepository,
                messagingTemplate);

        alice = user("alice0000001", "Alice", "Smith", "alice@test.com");
        bob   = user("bob00000001", "Bob",   "Jones", "bob@test.com");
        charlie = user("charlie0001", "Charlie", "Brown", "charlie@test.com");
    }

    // ─── createOrGetConversation ──────────────────────────────────────────────

    @Nested
    @DisplayName("createOrGetConversation()")
    class CreateOrGetConversation {

        @Test
        @DisplayName("Reuses existing DIRECT thread when one already exists between the two users")
        void reusesExistingDirectConversation() {
            Conversation existing = conversation(ConversationType.DIRECT, alice, bob);
            when(conversationRepository.findDirectBetween(
                    ConversationType.DIRECT, alice.getId(), bob.getId()))
                    .thenReturn(Optional.of(existing));

            ConversationResponse response = chatService.createOrGetConversation(
                    directRequest(bob.getId()), alice);

            assertThat(response.getId()).isEqualTo(existing.getId());
            verify(conversationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Creates new DIRECT conversation when none exists yet")
        void createsNewDirectConversation() {
            when(conversationRepository.findDirectBetween(
                    ConversationType.DIRECT, alice.getId(), bob.getId()))
                    .thenReturn(Optional.empty());
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            chatService.createOrGetConversation(directRequest(bob.getId()), alice);

            verify(conversationRepository, atLeast(1)).save(any(Conversation.class));
        }

        @Test
        @DisplayName("Always creates a new conversation for GROUP type")
        void alwaysCreatesNewGroupConversation() {
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            CreateConversationRequest req = new CreateConversationRequest();
            req.setTitle("Project Room");
            req.setParticipantIds(List.of(bob.getId()));

            chatService.createOrGetConversation(req, alice);

            // Must NOT attempt to look up an existing DM thread
            verify(conversationRepository, never())
                    .findDirectBetween(any(), any(), any());
            verify(conversationRepository, atLeast(1)).save(any(Conversation.class));
        }

        @Test
        @DisplayName("Caller is always added as a participant regardless of the request list")
        void callerIsAlwaysAParticipant() {
            when(conversationRepository.findDirectBetween(
                    ConversationType.DIRECT, alice.getId(), bob.getId()))
                    .thenReturn(Optional.empty());
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));

            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            when(conversationRepository.save(captor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            chatService.createOrGetConversation(directRequest(bob.getId()), alice);

            // At least one save should include Alice in the participants list
            boolean alicePresent = captor.getAllValues().stream()
                    .flatMap(c -> c.getParticipants().stream())
                    .anyMatch(p -> p.getUser().getId().equals(alice.getId()));
            assertThat(alicePresent).isTrue();
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when a participant userId does not exist")
        void throwsWhenParticipantNotFound() {
            when(conversationRepository.findDirectBetween(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(userRepository.findById("ghost")).thenReturn(Optional.empty());

            CreateConversationRequest req = directRequest("ghost");

            assertThatThrownBy(() -> chatService.createOrGetConversation(req, alice))
                    .isInstanceOf(group.moniepoint.eventsnestserver.exception.ResourceNotFoundException.class);
        }
    }

    // ─── listMyConversations ──────────────────────────────────────────────────

    @Nested
    @DisplayName("listMyConversations()")
    class ListMyConversations {

        @Test
        @DisplayName("Delegates to repository and maps results to ConversationResponse")
        void mapsRepositoryResults() {
            Conversation c1 = conversation(ConversationType.DIRECT, alice, bob);
            Conversation c2 = conversation(ConversationType.DIRECT, alice, charlie);
            when(conversationRepository.findAllByParticipantId(alice.getId()))
                    .thenReturn(List.of(c1, c2));

            List<ConversationResponse> result = chatService.listMyConversations(alice);

            assertThat(result).hasSize(2);
            verify(conversationRepository).findAllByParticipantId(alice.getId());
        }

        @Test
        @DisplayName("Returns empty list when user has no conversations")
        void returnsEmptyList() {
            when(conversationRepository.findAllByParticipantId(alice.getId()))
                    .thenReturn(List.of());

            assertThat(chatService.listMyConversations(alice)).isEmpty();
        }
    }

    // ─── sendMessage ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendMessage()")
    class SendMessage {

        @Test
        @DisplayName("Persists message and broadcasts it to the conversation topic")
        void persistsAndBroadcasts() {
            UUID convId = UUID.randomUUID();
            Conversation conv = conversation(ConversationType.DIRECT, alice, bob);
            conv.setId(convId);

            when(conversationRepository.isParticipant(convId, alice.getId())).thenReturn(true);
            when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });
            when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MessageResponse response = chatService.sendMessage(sendRequest(convId, "Hello!"), alice);

            assertThat(response.getContent()).isEqualTo("Hello!");
            assertThat(response.getSenderId()).isEqualTo(alice.getId());
            assertThat(response.getMessageType()).isEqualTo("TEXT");

            verify(messageRepository).save(any(Message.class));
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/conversation." + convId), any(MessageResponse.class));
        }

        @Test
        @DisplayName("Throws UnauthorizedException when caller is not a participant")
        void throwsWhenNotParticipant() {
            UUID convId = UUID.randomUUID();
            when(conversationRepository.isParticipant(convId, charlie.getId())).thenReturn(false);

            assertThatThrownBy(() -> chatService.sendMessage(sendRequest(convId, "Hi"), charlie))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("not a participant");

            verify(messageRepository, never()).save(any());
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        }

        @Test
        @DisplayName("Defaults to TEXT message type when none is specified")
        void defaultsToTextType() {
            UUID convId = UUID.randomUUID();
            Conversation conv = conversation(ConversationType.DIRECT, alice, bob);
            conv.setId(convId);

            when(conversationRepository.isParticipant(convId, alice.getId())).thenReturn(true);
            when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
            when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SendMessageRequest req = sendRequest(convId, "Test");
            req.setMessageType(null);

            MessageResponse response = chatService.sendMessage(req, alice);

            assertThat(response.getMessageType()).isEqualTo("TEXT");
        }

        @Test
        @DisplayName("Bumps conversation updatedAt after every message")
        void bumpsConversationUpdatedAt() {
            UUID convId = UUID.randomUUID();
            Conversation conv = conversation(ConversationType.DIRECT, alice, bob);
            conv.setId(convId);

            when(conversationRepository.isParticipant(convId, alice.getId())).thenReturn(true);
            when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
            when(conversationRepository.save(convCaptor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            chatService.sendMessage(sendRequest(convId, "Ping"), alice);

            // The final save on the conversation should have updatedAt set
            Conversation saved = convCaptor.getValue();
            assertThat(saved.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Accepts FILE message type when explicitly set")
        void acceptsFileMessageType() {
            UUID convId = UUID.randomUUID();
            Conversation conv = conversation(ConversationType.DIRECT, alice, bob);
            conv.setId(convId);

            when(conversationRepository.isParticipant(convId, alice.getId())).thenReturn(true);
            when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });
            when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SendMessageRequest req = sendRequest(convId, "contract.pdf");
            req.setMessageType("FILE");

            MessageResponse response = chatService.sendMessage(req, alice);

            assertThat(response.getMessageType()).isEqualTo("FILE");
        }
    }

    // ─── getHistory ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getHistory()")
    class GetHistory {

        @Test
        @DisplayName("Calls findLatestByConversationId when no cursor is provided")
        void callsLatestQueryWithoutCursor() {
            UUID convId = UUID.randomUUID();
            when(conversationRepository.isParticipant(convId, alice.getId())).thenReturn(true);
            when(messageRepository.findLatestByConversationId(eq(convId), any(Pageable.class)))
                    .thenReturn(List.of());

            chatService.getHistory(convId, null, 20, alice);

            verify(messageRepository).findLatestByConversationId(eq(convId), any(Pageable.class));
            verify(messageRepository, never()).findBeforeMessage(any(), any(), any());
        }

        @Test
        @DisplayName("Calls findBeforeMessage when a cursor id is provided")
        void callsCursorQueryWithBeforeId() {
            UUID convId   = UUID.randomUUID();
            UUID beforeId = UUID.randomUUID();
            when(conversationRepository.isParticipant(convId, alice.getId())).thenReturn(true);
            when(messageRepository.findBeforeMessage(eq(convId), eq(beforeId), any(Pageable.class)))
                    .thenReturn(List.of());

            chatService.getHistory(convId, beforeId, 20, alice);

            verify(messageRepository).findBeforeMessage(eq(convId), eq(beforeId), any(Pageable.class));
            verify(messageRepository, never()).findLatestByConversationId(any(), any());
        }

        @Test
        @DisplayName("Throws UnauthorizedException when caller is not a participant")
        void throwsWhenNotParticipant() {
            UUID convId = UUID.randomUUID();
            when(conversationRepository.isParticipant(convId, charlie.getId())).thenReturn(false);

            assertThatThrownBy(() -> chatService.getHistory(convId, null, 20, charlie))
                    .isInstanceOf(UnauthorizedException.class);

            verify(messageRepository, never()).findLatestByConversationId(any(), any());
        }

        @Test
        @DisplayName("Caps the page size at 50 regardless of requested limit")
        void capsPageSizeAt50() {
            UUID convId = UUID.randomUUID();
            when(conversationRepository.isParticipant(convId, alice.getId())).thenReturn(true);
            when(messageRepository.findLatestByConversationId(eq(convId), any(Pageable.class)))
                    .thenReturn(List.of());

            chatService.getHistory(convId, null, 9999, alice);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(messageRepository).findLatestByConversationId(any(), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("Returns results in chronological order (oldest first)")
        void returnsChronologicalOrder() {
            UUID convId = UUID.randomUUID();
            when(conversationRepository.isParticipant(convId, alice.getId())).thenReturn(true);

            Message m1 = message(conv(convId), alice, "First",  LocalDateTime.now().minusMinutes(2));
            Message m2 = message(conv(convId), alice, "Second", LocalDateTime.now().minusMinutes(1));
            Message m3 = message(conv(convId), alice, "Third",  LocalDateTime.now());

            // Repository returns DESC (newest first)
            when(messageRepository.findLatestByConversationId(eq(convId), any(Pageable.class)))
                    .thenReturn(List.of(m3, m2, m1));

            List<MessageResponse> history = chatService.getHistory(convId, null, 20, alice);

            // Service reverses to chronological
            assertThat(history).extracting(MessageResponse::getContent)
                    .containsExactly("First", "Second", "Third");
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User user(String id, String first, String last, String email) {
        return User.builder()
                .id(id).firstName(first).lastName(last)
                .email(email).passwordHash("hashed").role(Role.USER)
                .build();
    }

    private Conversation conversation(ConversationType type, User creator, User other) {
        List<ConversationParticipant> parts = new ArrayList<>();
        Conversation conv = Conversation.builder()
                .id(UUID.randomUUID())
                .type(type)
                .createdBy(creator)
                .participants(parts)
                .build();

        parts.add(ConversationParticipant.builder().conversation(conv).user(creator).build());
        parts.add(ConversationParticipant.builder().conversation(conv).user(other).build());
        return conv;
    }

    private Conversation conv(UUID id) {
        return Conversation.builder().id(id).participants(new ArrayList<>()).build();
    }

    private Message message(Conversation conv, User sender, String content, LocalDateTime sentAt) {
        Message m = Message.builder()
                .id(UUID.randomUUID())
                .conversation(conv)
                .sender(sender)
                .content(content)
                .messageType(MessageType.TEXT)
                .build();
        m.setSentAt(sentAt);
        return m;
    }

    private CreateConversationRequest directRequest(String otherUserId) {
        CreateConversationRequest req = new CreateConversationRequest();
        req.setParticipantIds(List.of(otherUserId));
        return req;
    }

    private SendMessageRequest sendRequest(UUID convId, String content) {
        SendMessageRequest req = new SendMessageRequest();
        req.setConversationId(convId);
        req.setContent(content);
        return req;
    }
}
