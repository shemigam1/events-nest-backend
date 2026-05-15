package group.moniepoint.eventsnestserver.chat.repository;

import group.moniepoint.eventsnestserver.chat.model.Conversation;
import group.moniepoint.eventsnestserver.chat.model.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("""
            SELECT DISTINCT c FROM Conversation c
            JOIN FETCH c.participants p
            JOIN FETCH p.user
            WHERE c.id IN (
                SELECT cp.conversation.id FROM ConversationParticipant cp WHERE cp.user.id = :userId
            )
            ORDER BY c.updatedAt DESC
            """)
    List<Conversation> findAllByParticipantId(@Param("userId") String userId);

    @Query("""
            SELECT c FROM Conversation c
            WHERE c.type = :type
              AND EXISTS (SELECT p FROM ConversationParticipant p WHERE p.conversation = c AND p.user.id = :userA)
              AND EXISTS (SELECT p FROM ConversationParticipant p WHERE p.conversation = c AND p.user.id = :userB)
            """)
    Optional<Conversation> findDirectBetween(
            @Param("type") ConversationType type,
            @Param("userA") String userA,
            @Param("userB") String userB);

    Optional<Conversation> findByTitleAndEventId(String title, UUID eventId);

    @Query("""
            SELECT COUNT(p) > 0 FROM ConversationParticipant p
            WHERE p.conversation.id = :conversationId AND p.user.id = :userId
            """)
    boolean isParticipant(@Param("conversationId") UUID conversationId,
                          @Param("userId") String userId);
}
