package group.moniepoint.eventsnestserver.chat.repository;

import group.moniepoint.eventsnestserver.chat.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("""
            SELECT m FROM Message m
            WHERE m.conversation.id = :conversationId
            ORDER BY m.sentAt DESC, m.id DESC
            """)
    List<Message> findLatestByConversationId(
            @Param("conversationId") UUID conversationId,
            Pageable pageable);

    @Query("""
            SELECT m FROM Message m
            WHERE m.conversation.id = :conversationId
              AND (m.sentAt < (SELECT m2.sentAt FROM Message m2 WHERE m2.id = :beforeId)
                   OR (m.sentAt = (SELECT m2.sentAt FROM Message m2 WHERE m2.id = :beforeId)
                       AND m.id < :beforeId))
            ORDER BY m.sentAt DESC, m.id DESC
            """)
    List<Message> findBeforeMessage(
            @Param("conversationId") UUID conversationId,
            @Param("beforeId") UUID beforeId,
            Pageable pageable);
}
