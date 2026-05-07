package group.moniepoint.eventsnestserver.events.repository;

import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventMembershipRepository extends JpaRepository<EventMembership, UUID> {

    boolean existsByEventsIdAndUserIdAndRole(UUID eventId, String userId, EventRole role);
}
