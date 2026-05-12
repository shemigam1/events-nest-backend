package group.moniepoint.eventsnestserver.checkin.repository;

import group.moniepoint.eventsnestserver.checkin.model.TicketCheckIn;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TicketCheckInRepository extends JpaRepository<TicketCheckIn, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO ticket_check_ins (id, ticket_id, event_day_id, checked_in_at, checked_in_by_label)
            VALUES (:id, :ticketId, :eventDayId, :checkedInAt, :label)
            ON CONFLICT (ticket_id, event_day_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("ticketId") UUID ticketId,
                       @Param("eventDayId") UUID eventDayId,
                       @Param("checkedInAt") LocalDateTime checkedInAt,
                       @Param("label") String label);

    @Query("""
            SELECT COUNT(DISTINCT t.id)
            FROM Ticket t
            WHERE t.status = :used
               OR EXISTS (SELECT 1 FROM TicketCheckIn c WHERE c.ticket.id = t.id)
            """)
    long countTicketsWithAnyCheckIn(@Param("used") TicketStatus used);

    @Query("SELECT COUNT(c) FROM TicketCheckIn c WHERE c.eventDay.event.id = :eventId")
    long countByEventId(@Param("eventId") UUID eventId);

    @Query("""
            SELECT ed.dayNumber, COUNT(c)
            FROM TicketCheckIn c JOIN c.eventDay ed
            WHERE ed.event.id = :eventId
            GROUP BY ed.dayNumber
            ORDER BY ed.dayNumber
            """)
    List<Object[]> countByDayForEvent(@Param("eventId") UUID eventId);
}
