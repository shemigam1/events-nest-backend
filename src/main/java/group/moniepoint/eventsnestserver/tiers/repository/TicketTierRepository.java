package group.moniepoint.eventsnestserver.tiers.repository;

import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketTierRepository extends JpaRepository<TicketTier, UUID> {

    @EntityGraph(attributePaths = "event")
    List<TicketTier> findAllByEventId(UUID eventId);
}
