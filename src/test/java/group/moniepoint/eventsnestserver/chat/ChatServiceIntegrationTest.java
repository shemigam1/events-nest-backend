package group.moniepoint.eventsnestserver.chat;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.chat.dto.request.CreateConversationRequest;
import group.moniepoint.eventsnestserver.chat.dto.request.SendMessageRequest;
import group.moniepoint.eventsnestserver.chat.dto.response.ConversationResponse;
import group.moniepoint.eventsnestserver.chat.dto.response.MessageResponse;
import group.moniepoint.eventsnestserver.chat.model.ConversationType;
import group.moniepoint.eventsnestserver.chat.repository.ConversationRepository;
import group.moniepoint.eventsnestserver.chat.repository.MessageRepository;
import group.moniepoint.eventsnestserver.chat.service.ChatService;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for ChatService.
 *
 * Runs against an H2 in-memory database (schema built from JPA entities via
 * create-drop). SimpMessagingTemplate is mocked so no WebSocket context is
 * needed — the broadcast side-effect is verified separately in WS tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Chat Service Integration Tests")
@Import(IntegrationTestConfig.class)
class ChatServiceIntegrationTest {

    @Autowired private ChatService chatService;
    @Autowired private UserRepository userRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;

    private User alice;
    private User bob;
    private User charlie;

    @BeforeEach
    void seedUsers() {
        alice = userRepository.save(User.builder()
                .id("alice0000001")
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@test.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .build());

        bob = userRepository.save(User.builder()
                .id("bob00000001")
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@test.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .build());

        charlie = userRepository.save(User.builder()
                .id("charlie00001")
                .firstName("Charlie")
                .lastName("Brown")
                .email("charlie@test.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .build());
    }

    // ─── createOrGetConversation ──────────────────────────────────────────────

    @Nested
    @DisplayName("createOrGetConversation()")
    class CreateOrGetConversation {

        @Test
        @DisplayName("Creates a new DIRECT conversation between two users")
        void createsDirectConversation() {
            CreateConversationRequest req = directRequest(bob.getId());

            ConversationResponse response = chatService.createOrGetConversation(req, alice);

            assertThat(response.getId()).isNotNull();
            assertThat(response.getType()).isEqualTo("DIRECT");
            assertThat(response.getParticipants()).hasSize(2);
            assertThat(response.getParticipants())
                    .extracting(ConversationResponse.ParticipantSummary::getEmail)
                    .containsExactlyInAnyOrder("alice@test.com", "bob@test.com");
        }

        @Test
        @DisplayName("Returns the existing DIRECT thread instead of creating a duplicate")
        void returnsExistingDirectConversation() {
            CreateConversationRequest req = directRequest(bob.getId());

            ConversationResponse first  = chatService.createOrGetConversation(req, alice);
            ConversationResponse second = chatService.createOrGetConversation(req, alice);

            assertThat(second.getId()).isEqualTo(first.getId());
            assertThat(conversationRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Creates a GROUP conversation with multiple participants")
        void createsGroupConversation() {
            CreateConversationRequest req = new CreateConversationRequest();
            req.setTitle("Event Planning");
            req.setParticipantIds(List.of(bob.getId(), charlie.getId()));

            ConversationResponse response = chatService.createOrGetConversation(req, alice);

            assertThat(response.getType()).isEqualTo("GROUP");
            assertThat(response.getTitle()).isEqualTo("Event Planning");
            assertThat(response.getParticipants()).hasSize(3);
        }

        @Test
        @DisplayName("Always creates a new GROUP conversation even for the same participants")
        void alwaysCreatesNewGroupConversation() {
            CreateConversationRequest req = new CreateConversationRequest();
            req.setTitle("Room A");
            req.setParticipantIds(List.of(bob.getId()));

            chatService.createOrGetConversation(req, alice);
            chatService.createOrGetConversation(req, alice);

            // DIRECT would reuse — GROUP always creates fresh
            assertThat(conversationRepository.count()).isEqualTo(2);
        }
    }

    // ─── listMyConversations ──────────────────────────────────────────────────

    @Nested
    @DisplayName("listMyConversations()")
    class ListMyConversations {

        @Test
        @DisplayName("Returns all conversations the caller participates in")
        void returnsAllParticipantConversations() {
            chatService.createOrGetConversation(directRequest(bob.getId()), alice);
            chatService.createOrGetConversation(directRequest(charlie.getId()), alice);

            List<ConversationResponse> mine = chatService.listMyConversations(alice);

            assertThat(mine).hasSize(2);
        }

        @Test
        @DisplayName("Does not return conversations the caller is not part of")
        void excludesConversationsCallerIsNotIn() {
            // Bob and Charlie chat — Alice is not involved
            chatService.createOrGetConversation(directRequest(charlie.getId()), bob);

            List<ConversationResponse> aliceMine = chatService.listMyConversations(alice);

            assertThat(aliceMine).isEmpty();
        }

        @Test
        @DisplayName("Returns empty list when caller has no conversations")
        void returnsEmptyListForNewUser() {
            assertThat(chatService.listMyConversations(charlie)).isEmpty();
        }
    }

    // ─── sendMessage ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendMessage()")
    class SendMessage {

        @Test
        @DisplayName("Persists the message and returns a populated MessageResponse")
        void persistsMessage() {
            ConversationResponse conv = chatService.createOrGetConversation(directRequest(bob.getId()), alice);

            SendMessageRequest req = sendRequest(conv.getId(), "Hello Bob!");
            MessageResponse response = chatService.sendMessage(req, alice);

            assertThat(response.getId()).isNotNull();
            assertThat(response.getContent()).isEqualTo("Hello Bob!");
            assertThat(response.getSenderId()).isEqualTo(alice.getId());
            assertThat(response.getSenderName()).isEqualTo("Alice Smith");
            assertThat(response.getConversationId()).isEqualTo(conv.getId());
            assertThat(response.getMessageType()).isEqualTo("TEXT");
            assertThat(response.getSentAt()).isNotNull();

            assertThat(messageRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Throws UnauthorizedException when sender is not a participant")
        void throwsWhenSenderIsNotParticipant() {
            // Alice and Bob create a conversation — Charlie is NOT a participant
            ConversationResponse conv = chatService.createOrGetConversation(directRequest(bob.getId()), alice);

            SendMessageRequest req = sendRequest(conv.getId(), "Intruder!");

            assertThatThrownBy(() -> chatService.sendMessage(req, charlie))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("not a participant");
        }

        @Test
        @DisplayName("Throws when conversation id does not exist")
        void throwsForNonExistentConversation() {
            SendMessageRequest req = sendRequest(UUID.randomUUID(), "Hello?");

            assertThatThrownBy(() -> chatService.sendMessage(req, alice))
                    .isInstanceOf(UnauthorizedException.class); // isParticipant returns false
        }

        @Test
        @DisplayName("Both participants can send messages in the same conversation")
        void bothParticipantsCanSend() {
            ConversationResponse conv = chatService.createOrGetConversation(directRequest(bob.getId()), alice);

            chatService.sendMessage(sendRequest(conv.getId(), "Hi Bob"), alice);
            chatService.sendMessage(sendRequest(conv.getId(), "Hi Alice"), bob);

            assertThat(messageRepository.count()).isEqualTo(2);
        }
    }

    // ─── getHistory ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getHistory()")
    class GetHistory {

        @Test
        @DisplayName("Returns messages in chronological order (oldest first)")
        void returnsChronologicalOrder() {
            ConversationResponse conv = chatService.createOrGetConversation(directRequest(bob.getId()), alice);

            chatService.sendMessage(sendRequest(conv.getId(), "First"),  alice);
            chatService.sendMessage(sendRequest(conv.getId(), "Second"), alice);
            chatService.sendMessage(sendRequest(conv.getId(), "Third"),  alice);

            List<MessageResponse> history = chatService.getHistory(conv.getId(), null, 20, alice);

            assertThat(history).hasSize(3);
            assertThat(history).extracting(MessageResponse::getContent)
                    .containsExactly("First", "Second", "Third");
        }

        @Test
        @DisplayName("Respects the limit parameter")
        void respectsLimit() {
            ConversationResponse conv = chatService.createOrGetConversation(directRequest(bob.getId()), alice);

            for (int i = 1; i <= 10; i++) {
                chatService.sendMessage(sendRequest(conv.getId(), "Message " + i), alice);
            }

            List<MessageResponse> history = chatService.getHistory(conv.getId(), null, 5, alice);

            assertThat(history).hasSize(5);
            // With limit=5 and DESC fetch, we get the 5 newest — then they're reversed to chronological.
            assertThat(history).extracting(MessageResponse::getContent)
                    .containsExactly("Message 6", "Message 7", "Message 8", "Message 9", "Message 10");
        }

        @Test
        @DisplayName("Throws UnauthorizedException when caller is not a participant")
        void throwsWhenNotParticipant() {
            ConversationResponse conv = chatService.createOrGetConversation(directRequest(bob.getId()), alice);

            assertThatThrownBy(() -> chatService.getHistory(conv.getId(), null, 20, charlie))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Returns empty list when conversation has no messages")
        void returnsEmptyWhenNoMessages() {
            ConversationResponse conv = chatService.createOrGetConversation(directRequest(bob.getId()), alice);

            List<MessageResponse> history = chatService.getHistory(conv.getId(), null, 20, alice);

            assertThat(history).isEmpty();
        }

        @Test
        @DisplayName("Cursor-based pagination returns messages older than the given message")
        void cursorPaginationReturnsOlderMessages() {
            ConversationResponse conv = chatService.createOrGetConversation(directRequest(bob.getId()), alice);

            for (int i = 1; i <= 5; i++) {
                chatService.sendMessage(sendRequest(conv.getId(), "Msg " + i), alice);
            }

            // First page: last 3 messages (Msg 3, 4, 5)
            List<MessageResponse> firstPage = chatService.getHistory(conv.getId(), null, 3, alice);
            assertThat(firstPage).hasSize(3);

            // Use the oldest message on first page as the cursor
            UUID cursorId = firstPage.get(0).getId(); // "Msg 3"

            // Second page: messages before Msg 3 → should be Msg 1, Msg 2
            List<MessageResponse> secondPage = chatService.getHistory(conv.getId(), cursorId, 10, alice);
            assertThat(secondPage).hasSize(2);
            assertThat(secondPage).extracting(MessageResponse::getContent)
                    .containsExactly("Msg 1", "Msg 2");
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CreateConversationRequest directRequest(String otherUserId) {
        CreateConversationRequest req = new CreateConversationRequest();
        req.setParticipantIds(List.of(otherUserId));
        return req;
    }

    private SendMessageRequest sendRequest(UUID conversationId, String content) {
        SendMessageRequest req = new SendMessageRequest();
        req.setConversationId(conversationId);
        req.setContent(content);
        return req;
    }
}
