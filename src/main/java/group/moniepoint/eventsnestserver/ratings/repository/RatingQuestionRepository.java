package group.moniepoint.eventsnestserver.ratings.repository;

import group.moniepoint.eventsnestserver.ratings.model.RatingQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RatingQuestionRepository extends JpaRepository<RatingQuestion, UUID> {

    List<RatingQuestion> findAllByFormIdOrderByDisplayOrderAsc(UUID formId);
}
