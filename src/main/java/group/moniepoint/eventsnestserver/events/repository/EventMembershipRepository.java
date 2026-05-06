package group.moniepoint.eventsnestserver.events.repository;

import group.moniepoint.eventsnestserver.events.models.EventMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventMembershipRepository extends JpaRepository<EventMembership, UUID> {


}
