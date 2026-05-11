package group.moniepoint.eventsnestserver.ratings.repository;

import group.moniepoint.eventsnestserver.ratings.model.RatingResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RatingResponseRepository extends JpaRepository<RatingResponse, UUID> {

    List<RatingResponse> findAllByFormId(UUID formId);

    Optional<RatingResponse> findByFormIdAndRespondentId(UUID formId, String respondentId);

    @Query("SELECT COUNT(r) FROM RatingResponse r WHERE r.form.id = :formId")
    long countByFormId(@Param("formId") UUID formId);
}
