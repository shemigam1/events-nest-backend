package group.moniepoint.eventsnestserver.chat.repository;

import group.moniepoint.eventsnestserver.chat.model.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {

    @Query("SELECT p FROM ConversationParticipant p WHERE p.conversation.id = :conversationId AND p.user.id = :userId")
    Optional<ConversationParticipant> findByConversationIdAndUserId(
            @Param("conversationId") UUID conversationId,
            @Param("userId") String userId);
}
