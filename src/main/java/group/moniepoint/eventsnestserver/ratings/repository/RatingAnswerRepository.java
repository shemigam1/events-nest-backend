package group.moniepoint.eventsnestserver.ratings.repository;

import group.moniepoint.eventsnestserver.ratings.model.RatingAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RatingAnswerRepository extends JpaRepository<RatingAnswer, UUID> {

    List<RatingAnswer> findAllByResponseId(UUID responseId);
}
