package group.moniepoint.eventsnestserver.tickets;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.bookings.models.Booking;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.tickets.dto.response.TicketResponse;
import group.moniepoint.eventsnestserver.tickets.models.Ticket;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import group.moniepoint.eventsnestserver.tickets.service.TicketServiceImpl;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    private TicketServiceImpl ticketService;

    private User attendee;
    private TicketTier tier;
    private Booking booking;

    @BeforeEach
    void setUp() {
        ticketService = new TicketServiceImpl(ticketRepository);

        attendee = User.builder()
                .id("attendee0001")
                .email("attendee@example.com")
                .firstName("Test")
                .lastName("Attendee")
                .passwordHash("hashed")
                .role(Role.USER)
                .build();

        tier = TicketTier.builder()
                .id(UUID.randomUUID())
                .name("VIP")
                .price(new BigDecimal("100.00"))
                .rowPrefix("VIP")
                .rowCount(5)
                .seatsPerRow(10)
                .totalCapacity(50)
                .availableCapacity(50)
                .build();

        booking = Booking.builder()
                .id(UUID.randomUUID())
                .build();
    }

    // ─── issueTickets — seat assignment algorithm ───────────────────────────────

    @Test
    void issueTicketsAssignsSequentialSeatsWhenNoExistingTickets() {
        when(ticketRepository.countByTierIdAndStatusIn(tier.getId(),
                List.of(TicketStatus.VALID, TicketStatus.USED)))
                .thenReturn(0L);
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.issueTickets(booking, tier, attendee, 3);

        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());
        List<Ticket> issued = captor.getValue();

        assertThat(issued).extracting(Ticket::getSeatNumber)
                .containsExactly("VIP1-1", "VIP1-2", "VIP1-3");
    }

    @Test
    void issueTicketsContinuesFromExistingNonRefundedCount() {
        // 14 existing non-refunded tickets → next index is 14
        // index 14 in tier with seatsPerRow=10 → row=1, position=5 → VIP2-5
        when(ticketRepository.countByTierIdAndStatusIn(tier.getId(),
                List.of(TicketStatus.VALID, TicketStatus.USED)))
                .thenReturn(14L);
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.issueTickets(booking, tier, attendee, 2);

        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).extracting(Ticket::getSeatNumber)
                .containsExactly("VIP2-5", "VIP2-6");
    }

    @Test
    void issueTicketsRollsOverToNextRowAtRowBoundary() {
        // 9 existing → next 2 should be VIP1-10 then VIP2-1
        when(ticketRepository.countByTierIdAndStatusIn(tier.getId(),
                List.of(TicketStatus.VALID, TicketStatus.USED)))
                .thenReturn(9L);
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.issueTickets(booking, tier, attendee, 2);

        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).extracting(Ticket::getSeatNumber)
                .containsExactly("VIP1-10", "VIP2-1");
    }

    @Test
    void issueTicketsAssignsUniqueQrCodesAndValidStatus() {
        when(ticketRepository.countByTierIdAndStatusIn(any(), anyList())).thenReturn(0L);
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.issueTickets(booking, tier, attendee, 5);

        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());
        List<Ticket> issued = captor.getValue();

        assertThat(issued).hasSize(5);
        assertThat(issued).extracting(Ticket::getQrCode).doesNotHaveDuplicates();
        assertThat(issued).extracting(Ticket::getStatus).containsOnly(TicketStatus.VALID);
        assertThat(issued).extracting(Ticket::getAttendee).containsOnly(attendee);
    }

    // ─── refundTicketsForBooking ────────────────────────────────────────────────

    @Test
    void refundTicketsForBookingMarksAllAsRefunded() {
        UUID bookingId = UUID.randomUUID();
        Ticket t1 = Ticket.builder().status(TicketStatus.VALID).build();
        Ticket t2 = Ticket.builder().status(TicketStatus.VALID).build();
        when(ticketRepository.findAllByBookingId(bookingId)).thenReturn(new ArrayList<>(List.of(t1, t2)));

        ticketService.refundTicketsForBooking(bookingId);

        assertThat(t1.getStatus()).isEqualTo(TicketStatus.REFUNDED);
        assertThat(t2.getStatus()).isEqualTo(TicketStatus.REFUNDED);
        verify(ticketRepository).saveAll(List.of(t1, t2));
    }

    // ─── getMyTickets ────────────────────────────────────────────────────────────

    @Test
    void getMyTicketsReturnsMappedResponses() {
        Events event = new Events();
        event.setId(UUID.randomUUID());
        event.setTitle("Tech Summit");
        tier.setEvent(event);

        Ticket ticket = Ticket.builder()
                .id(UUID.randomUUID())
                .booking(booking)
                .tier(tier)
                .attendee(attendee)
                .seatNumber("VIP1-1")
                .qrCode("qr-abc")
                .status(TicketStatus.VALID)
                .build();
        when(ticketRepository.findAllByAttendeeId(attendee.getId())).thenReturn(List.of(ticket));

        List<TicketResponse> result = ticketService.getMyTickets(attendee);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSeatNumber()).isEqualTo("VIP1-1");
        assertThat(result.get(0).getQrCode()).isEqualTo("qr-abc");
        assertThat(result.get(0).getEventTitle()).isEqualTo("Tech Summit");
        assertThat(result.get(0).getTierName()).isEqualTo("VIP");
    }
}
