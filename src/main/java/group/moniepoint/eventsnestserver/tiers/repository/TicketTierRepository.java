package group.moniepoint.eventsnestserver.tiers.repository;

import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketTierRepository extends JpaRepository<TicketTier, UUID> {

    @EntityGraph(attributePaths = "event")
    List<TicketTier> findAllByEventId(UUID eventId);

    /**
     * Bulk-fetch tiers for a set of events in a single query.
     * Use this instead of looping findAllByEventId when building list responses —
     * avoids the N+1 pattern (one query per event → one query for all events).
     */
    @EntityGraph(attributePaths = "event")
    List<TicketTier> findAllByEventIdIn(List<UUID> eventIds);
}
