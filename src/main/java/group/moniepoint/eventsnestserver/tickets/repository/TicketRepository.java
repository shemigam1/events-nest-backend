package group.moniepoint.eventsnestserver.tickets.repository;

import group.moniepoint.eventsnestserver.tickets.models.Ticket;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    long countByTierIdAndStatusIn(UUID tierId, List<TicketStatus> statuses);

    long countByStatus(TicketStatus status);

    long countByStatusIn(List<TicketStatus> statuses);

    List<Ticket> findAllByBookingId(UUID bookingId);

    @EntityGraph(attributePaths = {"tier", "booking"})
    List<Ticket> findAllByAttendeeId(String attendeeId);

    @EntityGraph(attributePaths = {"tier", "tier.event", "attendee"})
    Optional<Ticket> findByQrCode(String qrCode);

    @EntityGraph(attributePaths = {"tier", "tier.event", "attendee"})
    Optional<Ticket> findByShortCode(String shortCode);

    @Modifying
    @Query("""
            UPDATE Ticket t
            SET t.status = :usedStatus,
                t.checkedInAt = :checkedInAt,
                t.checkedInByLabel = :label
            WHERE t.id = :id AND t.status = :validStatus
            """)
    int markAsCheckedIn(@Param("id") UUID id,
                        @Param("checkedInAt") LocalDateTime checkedInAt,
                        @Param("label") String label,
                        @Param("usedStatus") TicketStatus usedStatus,
                        @Param("validStatus") TicketStatus validStatus);
}
