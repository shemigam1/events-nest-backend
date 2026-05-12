package group.moniepoint.eventsnestserver.chat.repository;

import group.moniepoint.eventsnestserver.chat.model.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {
}
