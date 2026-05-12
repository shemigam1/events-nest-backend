package group.moniepoint.eventsnestserver.eventmanagers.repository;

import group.moniepoint.eventsnestserver.eventmanagers.model.EventManagerProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventManagerProfileRepository extends JpaRepository<EventManagerProfile, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<EventManagerProfile> findByUserId(String userId);

    boolean existsByUserIdAndSuspendedFalse(String userId);
}
