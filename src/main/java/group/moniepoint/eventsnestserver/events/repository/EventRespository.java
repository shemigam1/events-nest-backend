package group.moniepoint.eventsnestserver.events.repository;

import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRespository extends JpaRepository<Events, UUID> {

    @EntityGraph(attributePaths = "createdBy")
    Optional<Events> findById(UUID id);

    List<Events> findAllByStatus(EventStatus status);
}
