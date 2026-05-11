package group.moniepoint.eventsnestserver.events.repository;

import group.moniepoint.eventsnestserver.events.models.EventDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventDayRepository extends JpaRepository<EventDay, UUID> {

    List<EventDay> findAllByEventIdOrderByDayNumberAsc(UUID eventId);

    long countByEventId(UUID eventId);

    Optional<EventDay> findFirstByEventIdOrderByDayNumberDesc(UUID eventId);

    Optional<EventDay> findByIdAndEventId(UUID id, UUID eventId);
}
