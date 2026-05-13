package group.moniepoint.eventsnestserver.comments.repository;

import group.moniepoint.eventsnestserver.comments.model.CommentReaction;
import group.moniepoint.eventsnestserver.comments.model.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommentReactionRepository extends JpaRepository<CommentReaction, UUID> {

    Optional<CommentReaction> findByCommentIdAndUserIdAndType(UUID commentId, String userId, ReactionType type);

    long countByCommentIdAndType(UUID commentId, ReactionType type);

    boolean existsByCommentIdAndUserIdAndType(UUID commentId, String userId, ReactionType type);
}
