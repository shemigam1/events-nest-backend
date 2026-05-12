package group.moniepoint.eventsnestserver.checkin.port;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TicketLookupPort {
    Optional<CheckInTicketView> findByQrCode(String qrCode);
    Optional<CheckInTicketView> findByShortCode(String shortCode);

    /**
     * Inserts a {@code ticket_check_ins} row and updates the ticket row. Idempotent on
     * (ticket, event day): returns {@code 0} when that day was already checked in.
     *
     * @param dayScopedTier when true, the ticket must be VALID and is moved to USED; when false,
     *                      the ticket stays VALID (multi-day pass).
     */
    int tryRecordCheckIn(UUID ticketId,
                         String qrCode,
                         UUID eventDayId,
                         boolean dayScopedTier,
                         LocalDateTime checkedInAt,
                         String checkedInByLabel);
}
