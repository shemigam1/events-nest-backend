package group.moniepoint.eventsnestserver.eventmanagers.repository;

import group.moniepoint.eventsnestserver.eventmanagers.model.EventManagerInvitation;
import group.moniepoint.eventsnestserver.eventmanagers.model.ManagerInvitationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventManagerInvitationRepository extends JpaRepository<EventManagerInvitation, UUID> {

    @EntityGraph(attributePaths = {"event", "managerUser", "invitedBy"})
    Optional<EventManagerInvitation> findByEventIdAndManagerUserId(UUID eventId, String managerUserId);

    @EntityGraph(attributePaths = {"event", "managerUser", "invitedBy"})
    List<EventManagerInvitation> findAllByEventIdOrderByCreatedAtDesc(UUID eventId);

    @EntityGraph(attributePaths = {"event", "managerUser", "invitedBy"})
    List<EventManagerInvitation> findAllByManagerUserIdAndStatusOrderByCreatedAtDesc(
            String managerUserId, ManagerInvitationStatus status);
}
