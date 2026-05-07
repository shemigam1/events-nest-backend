package group.moniepoint.eventsnestserver.tickets.repository;

import group.moniepoint.eventsnestserver.tickets.models.Ticket;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    long countByTierIdAndStatusIn(UUID tierId, List<TicketStatus> statuses);

    List<Ticket> findAllByBookingId(UUID bookingId);

    @EntityGraph(attributePaths = {"tier", "booking"})
    List<Ticket> findAllByAttendeeId(String attendeeId);
}
