package group.moniepoint.eventsnestserver.events.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.bookings.repository.BookingRepository;
import group.moniepoint.eventsnestserver.checkin.repository.TicketCheckInRepository;
import group.moniepoint.eventsnestserver.events.dto.response.EventAnalyticsResponse;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.guestlist.repository.GuestRepository;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventAnalyticsServiceImpl implements EventAnalyticsService {

    private final EventRespository eventRepository;
    private final EventMembershipRepository membershipRepository;
    private final EventConfigRepository configRepository;
    private final TicketRepository ticketRepository;
    private final BookingRepository bookingRepository;
    private final TicketCheckInRepository checkInRepository;
    private final GuestRepository guestRepository;

    @Override
    @Transactional(readOnly = true)
    public EventAnalyticsResponse getAnalytics(UUID eventId, User organizer) {
        if (!eventRepository.existsById(eventId)) throw new EventNotFoundException();
        if (!membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER)) {
            throw new NotEventOrganizerException();
        }

        long ticketsSold = ticketRepository.countByEventId(eventId);
        BigDecimal revenue = bookingRepository.sumConfirmedRevenueByEventId(eventId);
        if (revenue == null) revenue = BigDecimal.ZERO;

        long checkedIn = checkInRepository.countByEventId(eventId);
        double checkInRate = ticketsSold == 0 ? 0.0 : (double) checkedIn / ticketsSold;

        List<EventAnalyticsResponse.DayCheckIn> checkInsByDay =
                checkInRepository.countByDayForEvent(eventId).stream()
                        .map(row -> EventAnalyticsResponse.DayCheckIn.builder()
                                .dayNumber(((Number) row[0]).intValue())
                                .count(((Number) row[1]).longValue())
                                .build())
                        .toList();

        List<EventAnalyticsResponse.TierStats> ticketsByTier =
                ticketRepository.countByTierForEvent(eventId).stream()
                        .map(row -> EventAnalyticsResponse.TierStats.builder()
                                .tierName((String) row[0])
                                .sold(((Number) row[1]).longValue())
                                .totalCapacity(((Number) row[2]).intValue())
                                .build())
                        .toList();

        List<EventAnalyticsResponse.DateCount> bookingsByDate =
                bookingRepository.countBookingsByDateForEvent(eventId).stream()
                        .map(row -> EventAnalyticsResponse.DateCount.builder()
                                .date(row[0].toString())
                                .count(((Number) row[1]).longValue())
                                .build())
                        .toList();

        Map<String, Long> rsvpSummary = null;
        boolean guestListEnabled = configRepository.findByEventId(eventId)
                .map(c -> c.isGuestListEnabled())
                .orElse(false);

        if (guestListEnabled) {
            rsvpSummary = new HashMap<>();
            for (Object[] row : guestRepository.countByRsvpStatusForEvent(eventId)) {
                rsvpSummary.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        return EventAnalyticsResponse.builder()
                .ticketsSold(ticketsSold)
                .totalRevenue(revenue)
                .checkInRate(checkInRate)
                .checkInsByDay(checkInsByDay)
                .ticketsByTier(ticketsByTier)
                .bookingsByDate(bookingsByDate)
                .rsvpSummary(rsvpSummary)
                .build();
    }
}
