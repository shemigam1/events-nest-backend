package group.moniepoint.eventsnestserver.events.repository;

import group.moniepoint.eventsnestserver.events.models.EventConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventConfigRepository extends JpaRepository<EventConfig, UUID> {

    Optional<EventConfig> findByEventId(UUID eventId);
}
