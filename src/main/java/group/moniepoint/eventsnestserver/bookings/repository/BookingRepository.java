package group.moniepoint.eventsnestserver.bookings.repository;

import group.moniepoint.eventsnestserver.bookings.models.Booking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = {"event", "tier", "attendee"})
    Optional<Booking> findById(UUID id);

    @EntityGraph(attributePaths = {"event", "tier"})
    List<Booking> findAllByAttendeeId(String attendeeId);

    boolean existsByTierId(UUID tierId);
}
