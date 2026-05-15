package group.moniepoint.eventsnestserver.seed;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.bookings.models.Booking;
import group.moniepoint.eventsnestserver.bookings.models.BookingStatus;
import group.moniepoint.eventsnestserver.bookings.models.PaymentStatus;
import group.moniepoint.eventsnestserver.bookings.repository.BookingRepository;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.tickets.models.Ticket;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeds confirmed bookings and valid tickets for PUBLISHED paid events.
 * Creates 3 attendees per event (rotated from the user pool, never the organiser).
 * Covers all 32 organisers × first two PUBLISHED events = up to ~96 bookings.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketSeeder {

    private final BookingRepository    bookingRepository;
    private final TicketRepository     ticketRepository;
    private final TicketTierRepository tierRepository;

    // (organiserIndex, eventIndex, attendeeOffset, quantity, preferVip)
    private record BookingSeed(int organiserIdx, int eventIdx, int attendeeOffset, int qty, boolean preferVip) {}

    // 3 bookings per organiser on their first published event, 1 on the second
    private static final List<BookingSeed> BOOKING_SEEDS;

    static {
        List<BookingSeed> seeds = new ArrayList<>();
        int numOrganisers = UserSeeder.USERS.length; // 32
        for (int i = 0; i < numOrganisers; i++) {
            // Three General-tier attendees on event 0
            seeds.add(new BookingSeed(i, 0, 5,  1, false));
            seeds.add(new BookingSeed(i, 0, 10, 2, false));
            seeds.add(new BookingSeed(i, 0, 15, 1, true));
            // One General-tier attendee on event 1
            seeds.add(new BookingSeed(i, 1, 7,  1, false));
        }
        BOOKING_SEEDS = List.copyOf(seeds);
    }

    void seed(List<User> users, List<List<Events>> eventsByUser) {
        int bookingCount = 0;
        int ticketCount  = 0;

        for (BookingSeed bs : BOOKING_SEEDS) {
            List<Events> orgEvents = eventsByUser.get(bs.organiserIdx());
            if (bs.eventIdx() >= orgEvents.size()) continue;

            Events event = orgEvents.get(bs.eventIdx());
            if (event.getStatus() != EventStatus.PUBLISHED) continue;

            // Pick attendee — rotate past the organiser index so we never book own event
            int attendeeIdx = (bs.organiserIdx() + bs.attendeeOffset()) % users.size();
            if (attendeeIdx == bs.organiserIdx()) {
                attendeeIdx = (attendeeIdx + 1) % users.size();
            }
            User attendee = users.get(attendeeIdx);

            // Find the requested tier (VIP if preferred and available, else General)
            List<TicketTier> tiers = tierRepository.findAllByEventId(event.getId());
            if (tiers.isEmpty()) continue;

            TicketTier tier = tiers.stream()
                    .filter(t -> bs.preferVip() ? t.getName().equalsIgnoreCase("VIP")
                                                : t.getName().equalsIgnoreCase("General"))
                    .findFirst()
                    .orElse(tiers.get(0));

            // Skip free tiers — only create paid bookings here
            if (tier.getPrice().compareTo(BigDecimal.ZERO) == 0) continue;

            // Guard: enough capacity
            if (tier.getAvailableCapacity() < bs.qty()) continue;

            BigDecimal total = tier.getPrice().multiply(BigDecimal.valueOf(bs.qty()));

            Booking booking = bookingRepository.save(Booking.builder()
                    .attendee(attendee)
                    .event(event)
                    .tier(tier)
                    .quantity(bs.qty())
                    .totalAmount(total)
                    .status(BookingStatus.CONFIRMED)
                    .paymentStatus(PaymentStatus.PAID)
                    .paymentReference("SEED-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .paymentGatewayRef("SEED-TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase())
                    .paidAt(LocalDateTime.now().minusDays(2))
                    .build());

            List<Ticket> tickets = issueTickets(booking, tier, attendee, bs.qty());
            ticketCount += tickets.size();

            // Reduce available capacity
            tier.setAvailableCapacity(tier.getAvailableCapacity() - bs.qty());
            tierRepository.save(tier);

            bookingCount++;
            log.debug("Seeded booking: {} × {} ticket(s) for [{}] {}",
                    attendee.getEmail(), bs.qty(), tier.getName(), event.getTitle());
        }

        log.info("Seeded {} bookings and {} tickets across published events", bookingCount, ticketCount);
    }

    private List<Ticket> issueTickets(Booking booking, TicketTier tier, User attendee, int quantity) {
        long existing = ticketRepository.countByTierIdAndStatusIn(
                tier.getId(), List.of(TicketStatus.VALID, TicketStatus.USED));

        List<Ticket> tickets = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            long index    = existing + i;
            int  row      = (int) (index / tier.getSeatsPerRow());
            int  position = (int) (index % tier.getSeatsPerRow()) + 1;
            String seat   = tier.getRowPrefix() + (row + 1) + "-" + position;

            tickets.add(Ticket.builder()
                    .booking(booking)
                    .tier(tier)
                    .attendee(attendee)
                    .seatNumber(seat)
                    .qrCode(UUID.randomUUID().toString())
                    .shortCode(NanoIdUtils.randomNanoId(
                            NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
                            NanoIdUtils.DEFAULT_ALPHABET, 8))
                    .status(TicketStatus.VALID)
                    .build());
        }
        return ticketRepository.saveAll(tickets);
    }
}
