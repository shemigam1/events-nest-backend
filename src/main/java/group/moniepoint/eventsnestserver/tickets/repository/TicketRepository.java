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

    @EntityGraph(attributePaths = {"tier", "tier.event", "booking"})
    List<Ticket> findAllByAttendeeId(String attendeeId);

    @EntityGraph(attributePaths = {"tier", "tier.event", "tier.eventDay", "attendee"})
    Optional<Ticket> findByQrCode(String qrCode);

    @EntityGraph(attributePaths = {"tier", "tier.event", "tier.eventDay", "attendee"})
    Optional<Ticket> findByShortCode(String shortCode);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Ticket t
            SET t.checkedInAt = :checkedInAt,
                t.checkedInByLabel = :label
            WHERE t.id = :id AND t.status = :validStatus
            """)
    int updateLastCheckInForValidPass(@Param("id") UUID id,
                                      @Param("checkedInAt") LocalDateTime checkedInAt,
                                      @Param("label") String label,
                                      @Param("validStatus") TicketStatus validStatus);

    @Query("SELECT COUNT(t) FROM Ticket t JOIN t.tier tt WHERE tt.event.id = :eventId")
    long countByEventId(@Param("eventId") UUID eventId);

    @Query("""
            SELECT tt.name, COUNT(t), tt.totalCapacity
            FROM Ticket t JOIN t.tier tt
            WHERE tt.event.id = :eventId
            GROUP BY tt.id, tt.name, tt.totalCapacity
            """)
    List<Object[]> countByTierForEvent(@Param("eventId") UUID eventId);

    @Query("SELECT DISTINCT t.attendee FROM Ticket t JOIN t.tier tt WHERE tt.event.id = :eventId AND t.status <> 'REFUNDED'")
    List<group.moniepoint.eventsnestserver.auth.model.User> findAttendeesByEventId(@Param("eventId") UUID eventId);
}
