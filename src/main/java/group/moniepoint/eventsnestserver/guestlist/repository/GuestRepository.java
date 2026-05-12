package group.moniepoint.eventsnestserver.guestlist.repository;

import group.moniepoint.eventsnestserver.guestlist.model.Guest;
import group.moniepoint.eventsnestserver.guestlist.model.RsvpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuestRepository extends JpaRepository<Guest, UUID> {

    List<Guest> findAllByEventIdOrderByInvitedAtDesc(UUID eventId);

    Optional<Guest> findByRsvpTokenHash(String tokenHash);

    boolean existsByEventIdAndEmail(UUID eventId, String email);

    /** Used by visibility gating on PRIVATE events. */
    boolean existsByEventIdAndEmailAndRsvpStatus(UUID eventId, String email, RsvpStatus rsvpStatus);

    @Query("SELECT g.rsvpStatus, COUNT(g) FROM Guest g WHERE g.event.id = :eventId GROUP BY g.rsvpStatus")
    List<Object[]> countByRsvpStatusForEvent(@Param("eventId") UUID eventId);

    List<Guest> findAllByEventId(UUID eventId);
}
