package group.moniepoint.eventsnestserver.events;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.bookings.repository.BookingRepository;
import group.moniepoint.eventsnestserver.checkin.repository.TicketCheckInRepository;
import group.moniepoint.eventsnestserver.events.dto.response.EventAnalyticsResponse;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.events.service.EventAnalyticsServiceImpl;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.guestlist.model.RsvpStatus;
import group.moniepoint.eventsnestserver.guestlist.repository.GuestRepository;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventAnalyticsServiceTest {

    @Mock private EventRespository eventRepository;
    @Mock private EventMembershipRepository membershipRepository;
    @Mock private EventConfigRepository configRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private TicketCheckInRepository checkInRepository;
    @Mock private GuestRepository guestRepository;

    private EventAnalyticsServiceImpl analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new EventAnalyticsServiceImpl(
                eventRepository, membershipRepository, configRepository,
                ticketRepository, bookingRepository, checkInRepository, guestRepository);
    }

    private User organizer(UUID eventId) {
        User user = User.builder().id("org-1").build();
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, "org-1", EventRole.ORGANIZER))
                .thenReturn(true);
        return user;
    }

    @Test
    void getAnalytics_returnsAggregatedMetrics() {
        UUID eventId = UUID.randomUUID();
        User org = organizer(eventId);

        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(ticketRepository.countByEventId(eventId)).thenReturn(100L);
        when(bookingRepository.sumConfirmedRevenueByEventId(eventId)).thenReturn(new BigDecimal("50000"));
        when(checkInRepository.countByEventId(eventId)).thenReturn(75L);
        List<Object[]> checkInRows = new java.util.ArrayList<>();
        checkInRows.add(new Object[]{1, 75L});
        List<Object[]> tierRows = new java.util.ArrayList<>();
        tierRows.add(new Object[]{"VIP", 30L, 50});
        when(checkInRepository.countByDayForEvent(eventId)).thenReturn(checkInRows);
        when(ticketRepository.countByTierForEvent(eventId)).thenReturn(tierRows);
        when(bookingRepository.countBookingsByDateForEvent(eventId)).thenReturn(List.of());
        when(configRepository.findByEventId(eventId))
                .thenReturn(Optional.of(EventConfig.builder().guestListEnabled(false).build()));

        EventAnalyticsResponse result = analyticsService.getAnalytics(eventId, org);

        assertThat(result.getTicketsSold()).isEqualTo(100L);
        assertThat(result.getTotalRevenue()).isEqualByComparingTo("50000");
        assertThat(result.getCheckInRate()).isEqualTo(0.75);
        assertThat(result.getRsvpSummary()).isNull();
    }

    @Test
    void getAnalytics_includesRsvpSummaryWhenGuestListEnabled() {
        UUID eventId = UUID.randomUUID();
        User org = organizer(eventId);

        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(ticketRepository.countByEventId(eventId)).thenReturn(10L);
        when(bookingRepository.sumConfirmedRevenueByEventId(eventId)).thenReturn(BigDecimal.ZERO);
        when(checkInRepository.countByEventId(eventId)).thenReturn(0L);
        when(checkInRepository.countByDayForEvent(eventId)).thenReturn(List.of());
        when(ticketRepository.countByTierForEvent(eventId)).thenReturn(List.of());
        when(bookingRepository.countBookingsByDateForEvent(eventId)).thenReturn(List.of());
        when(configRepository.findByEventId(eventId))
                .thenReturn(Optional.of(EventConfig.builder().guestListEnabled(true).build()));
        List<Object[]> rsvpRows = new java.util.ArrayList<>();
        rsvpRows.add(new Object[]{RsvpStatus.ACCEPTED, 5L});
        rsvpRows.add(new Object[]{RsvpStatus.PENDING, 3L});
        when(guestRepository.countByRsvpStatusForEvent(eventId)).thenReturn(rsvpRows);

        EventAnalyticsResponse result = analyticsService.getAnalytics(eventId, org);

        assertThat(result.getRsvpSummary()).isNotNull();
        assertThat(result.getRsvpSummary()).containsEntry("ACCEPTED", 5L);
        assertThat(result.getRsvpSummary()).containsEntry("PENDING", 3L);
    }

    @Test
    void getAnalytics_throwsWhenEventNotFound() {
        UUID eventId = UUID.randomUUID();
        User org = User.builder().id("org-1").build();

        when(eventRepository.existsById(eventId)).thenReturn(false);

        assertThatThrownBy(() -> analyticsService.getAnalytics(eventId, org))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void getAnalytics_throwsWhenNotOrganizer() {
        UUID eventId = UUID.randomUUID();
        User nonOrg = User.builder().id("other").build();

        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, "other", EventRole.ORGANIZER))
                .thenReturn(false);

        assertThatThrownBy(() -> analyticsService.getAnalytics(eventId, nonOrg))
                .isInstanceOf(NotEventOrganizerException.class);
    }

    @Test
    void getAnalytics_handlesNullRevenueAsZero() {
        UUID eventId = UUID.randomUUID();
        User org = organizer(eventId);

        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(ticketRepository.countByEventId(eventId)).thenReturn(0L);
        when(bookingRepository.sumConfirmedRevenueByEventId(eventId)).thenReturn(null);
        when(checkInRepository.countByEventId(eventId)).thenReturn(0L);
        when(checkInRepository.countByDayForEvent(eventId)).thenReturn(List.of());
        when(ticketRepository.countByTierForEvent(eventId)).thenReturn(List.of());
        when(bookingRepository.countBookingsByDateForEvent(eventId)).thenReturn(List.of());
        when(configRepository.findByEventId(eventId))
                .thenReturn(Optional.of(EventConfig.builder().guestListEnabled(false).build()));

        EventAnalyticsResponse result = analyticsService.getAnalytics(eventId, org);

        assertThat(result.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
