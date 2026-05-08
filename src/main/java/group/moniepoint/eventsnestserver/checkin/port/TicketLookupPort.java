package group.moniepoint.eventsnestserver.checkin.port;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TicketLookupPort {
    Optional<CheckInTicketView> findByQrCode(String qrCode);
    int markCheckedIn(UUID ticketId, String qrCode, LocalDateTime checkedInAt, String checkedInByLabel);
}
