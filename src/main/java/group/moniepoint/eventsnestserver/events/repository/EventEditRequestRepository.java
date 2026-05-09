package group.moniepoint.eventsnestserver.events.repository;

import group.moniepoint.eventsnestserver.events.models.EventEditRequest;
import group.moniepoint.eventsnestserver.events.models.EventEditStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventEditRequestRepository extends JpaRepository<EventEditRequest, UUID> {

    @EntityGraph(attributePaths = {"event", "submittedBy"})
    Optional<EventEditRequest> findByEventIdAndStatus(UUID eventId, EventEditStatus status);

    @EntityGraph(attributePaths = {"event", "submittedBy"})
    Page<EventEditRequest> findAllByStatusOrderByCreatedAtAsc(EventEditStatus status, Pageable pageable);
}
