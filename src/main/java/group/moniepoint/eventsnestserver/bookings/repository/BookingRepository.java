package group.moniepoint.eventsnestserver.bookings.repository;

import group.moniepoint.eventsnestserver.bookings.models.Booking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = {"event", "tier", "attendee"})
    Optional<Booking> findById(UUID id);

    @EntityGraph(attributePaths = {"event", "tier", "attendee"})
    Optional<Booking> findByPaymentGatewayRef(String paymentGatewayRef);

    @EntityGraph(attributePaths = {"event", "tier"})
    List<Booking> findAllByAttendeeId(String attendeeId);

    @EntityGraph(attributePaths = {"attendee", "tier"})
    List<Booking> findAllByEventIdOrderByCreatedAtDesc(UUID eventId);

    boolean existsByTierId(UUID tierId);

    // Revenue / ticket counts now require BOTH status=CONFIRMED AND paymentStatus=PAID,
    // so PENDING-payment bookings (capacity reserved but money not received yet) don't
    // inflate the figures. Cancelled and refunded bookings are still excluded.

    @Query("SELECT COALESCE(SUM(b.quantity), 0) FROM Booking b WHERE b.event.createdBy.id = :organizerId AND b.status = 'CONFIRMED' AND b.paymentStatus = 'PAID'")
    long sumConfirmedTicketsByOrganizer(@Param("organizerId") String organizerId);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.event.createdBy.id = :organizerId AND b.status = 'CONFIRMED' AND b.paymentStatus = 'PAID'")
    java.math.BigDecimal sumConfirmedRevenueByOrganizer(@Param("organizerId") String organizerId);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.status = 'CONFIRMED' AND b.paymentStatus = 'PAID'")
    BigDecimal sumConfirmedRevenue();

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.event.id = :eventId AND b.status = 'CONFIRMED' AND b.paymentStatus = 'PAID'")
    long countConfirmedByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.event.id = :eventId AND b.status = 'CONFIRMED' AND b.paymentStatus = 'PAID'")
    BigDecimal sumConfirmedRevenueByEventId(@Param("eventId") UUID eventId);

    @Query(value = "SELECT CAST(created_at AS DATE), COUNT(*) FROM bookings WHERE event_id = :eventId AND status = 'CONFIRMED' AND payment_status = 'PAID' GROUP BY CAST(created_at AS DATE) ORDER BY CAST(created_at AS DATE)", nativeQuery = true)
    List<Object[]> countBookingsByDateForEvent(@Param("eventId") UUID eventId);
}
