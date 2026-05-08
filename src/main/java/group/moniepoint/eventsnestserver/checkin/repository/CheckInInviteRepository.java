package group.moniepoint.eventsnestserver.checkin.repository;

import group.moniepoint.eventsnestserver.checkin.model.CheckInInvite;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckInInviteRepository extends JpaRepository<CheckInInvite, UUID> {

    @EntityGraph(attributePaths = {"event"})
    Optional<CheckInInvite> findByTokenHash(String tokenHash);

    List<CheckInInvite> findAllByEventId(UUID eventId);
}
