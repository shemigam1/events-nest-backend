package group.moniepoint.eventsnestserver.events.repository;

import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRespository extends JpaRepository<Events, UUID> {

    @EntityGraph(attributePaths = "createdBy")
    Optional<Events> findById(UUID id);

    List<Events> findAllByStatus(EventStatus status);

    @EntityGraph(attributePaths = "createdBy")
    Page<Events> findAllByStatus(EventStatus status, Pageable pageable);

    @Query("SELECT e.status, COUNT(e) FROM Events e GROUP BY e.status")
    List<Object[]> countGroupedByStatus();
}
