package group.moniepoint.eventsnestserver.events.repository;

import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventMembershipRepository extends JpaRepository<EventMembership, UUID> {

    boolean existsByEventsIdAndUserIdAndRole(UUID eventId, String userId, EventRole role);

    void deleteByEventsIdAndUserIdAndRole(UUID eventId, String userId, EventRole role);

    Optional<EventMembership> findByEventsIdAndUserIdAndRole(UUID eventId, String userId, EventRole role);

    /** All members of a given role for one event (e.g. list all MANAGERs for an event). */
    @EntityGraph(attributePaths = {"user", "assignedBy"})
    List<EventMembership> findAllByEventsIdAndRole(UUID eventId, EventRole role);

    /** All events where a user holds a given role (e.g. manager dashboard). */
    @EntityGraph(attributePaths = {"events", "events.createdBy"})
    List<EventMembership> findAllByUserIdAndRole(String userId, EventRole role);
}
