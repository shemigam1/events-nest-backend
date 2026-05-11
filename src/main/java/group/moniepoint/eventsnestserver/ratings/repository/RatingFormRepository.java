package group.moniepoint.eventsnestserver.ratings.repository;

import group.moniepoint.eventsnestserver.ratings.model.RatingForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RatingFormRepository extends JpaRepository<RatingForm, UUID> {

    Optional<RatingForm> findByEventId(UUID eventId);

    /** Forms whose event has ended long enough ago to dispatch, and haven't been sent yet. */
    @Query("""
            SELECT rf FROM RatingForm rf JOIN rf.event e
            WHERE rf.sentAt IS NULL
              AND e.endTime <= :threshold
            """)
    List<RatingForm> findFormsReadyToDispatch(@Param("threshold") LocalDateTime threshold);
}
