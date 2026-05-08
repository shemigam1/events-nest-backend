package group.moniepoint.eventsnestserver.admin.service;

import group.moniepoint.eventsnestserver.admin.dto.request.RejectEventRequest;
import group.moniepoint.eventsnestserver.admin.dto.response.PageResponse;
import group.moniepoint.eventsnestserver.admin.dto.response.PlatformAnalyticsResponse;
import group.moniepoint.eventsnestserver.admin.dto.response.UserSummaryResponse;
import group.moniepoint.eventsnestserver.admin.kafka.AdminEventPublisher;
import group.moniepoint.eventsnestserver.admin.repository.AdminInvitationRepository;
import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.bookings.repository.BookingRepository;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.email.EmailService;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private EventRespository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketTierRepository tierRepository;
    @Mock private AdminInvitationRepository adminInvitationRepository;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AdminEventPublisher adminEventPublisher;

    private AdminServiceImpl adminService;

    @BeforeEach
    void setUp() {
        // Real ModelMapper, configured to match SecurityConfig (skip createdBy
        // since User -> String is a custom rule).
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.typeMap(Events.class, EventResponse.class)
                .addMappings(m -> m.skip(EventResponse::setCreatedBy));

        adminService = new AdminServiceImpl(
                modelMapper,
                eventRepository,
                userRepository,
                bookingRepository,
                ticketRepository,
                tierRepository,
                adminInvitationRepository,
                emailService,
                passwordEncoder,
                adminEventPublisher);
    }

    // ─── approveEvent ────────────────────────────────────────────────────────────

    @Test
    void approveEvent_movesPendingEventToPublishedAndClearsAnyRejectionReason() {
        UUID eventId = UUID.randomUUID();
        Events event = pendingEvent(eventId);
        event.setRejectionReason("previous reject reason");
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Events.class))).thenAnswer(inv -> inv.getArgument(0));

        EventsNestResponse<EventResponse> response = adminService.approveEvent(eventId);

        ArgumentCaptor<Events> savedCaptor = ArgumentCaptor.forClass(Events.class);
        verify(eventRepository).save(savedCaptor.capture());
        Events saved = savedCaptor.getValue();

        assertThat(saved.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(saved.getRejectionReason()).isNull();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void approveEvent_throwsWhenEventIsNotPendingApproval() {
        UUID eventId = UUID.randomUUID();
        Events event = pendingEvent(eventId);
        event.setStatus(EventStatus.DRAFT);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> adminService.approveEvent(eventId))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("pending approval");

        verify(eventRepository, never()).save(any());
    }

    @Test
    void approveEvent_throwsWhenEventNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.approveEvent(eventId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("event not found");
    }

    // ─── rejectEvent ─────────────────────────────────────────────────────────────

    @Test
    void rejectEvent_returnsEventToDraftAndStoresReason() {
        UUID eventId = UUID.randomUUID();
        Events event = pendingEvent(eventId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Events.class))).thenAnswer(inv -> inv.getArgument(0));

        RejectEventRequest request = new RejectEventRequest();
        request.setReason("venue not verified");

        EventsNestResponse<EventResponse> response = adminService.rejectEvent(eventId, request);

        ArgumentCaptor<Events> savedCaptor = ArgumentCaptor.forClass(Events.class);
        verify(eventRepository).save(savedCaptor.capture());
        Events saved = savedCaptor.getValue();

        assertThat(saved.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(saved.getRejectionReason()).isEqualTo("venue not verified");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(response.getData().getRejectionReason()).isEqualTo("venue not verified");
    }

    @Test
    void rejectEvent_throwsWhenEventIsNotPendingApproval() {
        UUID eventId = UUID.randomUUID();
        Events event = pendingEvent(eventId);
        event.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        RejectEventRequest request = new RejectEventRequest();
        request.setReason("any");

        assertThatThrownBy(() -> adminService.rejectEvent(eventId, request))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("pending approval");

        verify(eventRepository, never()).save(any());
    }

    @Test
    void rejectEvent_throwsWhenEventNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        RejectEventRequest request = new RejectEventRequest();
        request.setReason("any");

        assertThatThrownBy(() -> adminService.rejectEvent(eventId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── getEventsByStatus / getUsers ────────────────────────────────────────────

    @Test
    void getEventsByStatus_returnsPagedResponseMappedFromEntities() {
        Events e1 = pendingEvent(UUID.randomUUID());
        Events e2 = pendingEvent(UUID.randomUUID());
        Pageable pageable = PageRequest.of(0, 10);
        Page<Events> page = new PageImpl<>(List.of(e1, e2), pageable, 2);

        when(eventRepository.findAllByStatus(EventStatus.PENDING_APPROVAL, pageable)).thenReturn(page);

        PageResponse<EventResponse> response =
                adminService.getEventsByStatus(EventStatus.PENDING_APPROVAL, pageable);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getContent().get(0).getStatus()).isEqualTo(EventStatus.PENDING_APPROVAL);
    }

    @Test
    void getUsers_returnsPagedSummaryStrippingPasswordHash() {
        User user = User.builder()
                .id("nano-12chars")
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@example.com")
                .role(Role.USER)
                .enabled(true)
                .passwordHash("super-secret-hash")
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user), pageable, 1));

        PageResponse<UserSummaryResponse> response = adminService.getUsers(pageable);

        assertThat(response.getContent()).hasSize(1);
        UserSummaryResponse summary = response.getContent().get(0);
        assertThat(summary.getEmail()).isEqualTo("ada@example.com");
        assertThat(summary.getRole()).isEqualTo("USER");
        assertThat(summary.isEnabled()).isTrue();
        // UserSummaryResponse must not expose the password hash.
        assertThat(summary).hasNoNullFieldsOrPropertiesExcept("createdAt");
    }

    // ─── getAnalytics ────────────────────────────────────────────────────────────

    @Test
    void getAnalytics_aggregatesCountsRevenueAndCheckInRate() {
        when(eventRepository.countGroupedByStatus()).thenReturn(List.of(
                new Object[]{EventStatus.DRAFT, 3L},
                new Object[]{EventStatus.PUBLISHED, 7L}));
        when(bookingRepository.count()).thenReturn(42L);
        when(bookingRepository.sumConfirmedRevenue()).thenReturn(new BigDecimal("12345.67"));
        when(ticketRepository.countByStatus(TicketStatus.USED)).thenReturn(20L);
        when(ticketRepository.countByStatusIn(List.of(TicketStatus.VALID, TicketStatus.USED)))
                .thenReturn(50L);

        PlatformAnalyticsResponse response = adminService.getAnalytics();

        // Every EventStatus is represented; missing ones default to 0.
        assertThat(response.getEventsByStatus()).contains(
                entry("DRAFT", 3L),
                entry("PENDING_APPROVAL", 0L),
                entry("PUBLISHED", 7L),
                entry("CANCELLED", 0L));
        assertThat(response.getTotalBookings()).isEqualTo(42L);
        assertThat(response.getTotalRevenue()).isEqualByComparingTo("12345.67");
        assertThat(response.getCheckInRate()).isEqualTo(0.4);
    }

    @Test
    void getAnalytics_returnsZeroCheckInRateWhenNoTicketsIssued() {
        when(eventRepository.countGroupedByStatus()).thenReturn(List.of());
        when(bookingRepository.count()).thenReturn(0L);
        when(bookingRepository.sumConfirmedRevenue()).thenReturn(BigDecimal.ZERO);
        when(ticketRepository.countByStatus(TicketStatus.USED)).thenReturn(0L);
        when(ticketRepository.countByStatusIn(any())).thenReturn(0L);

        PlatformAnalyticsResponse response = adminService.getAnalytics();

        assertThat(response.getCheckInRate()).isEqualTo(0.0);
        assertThat(Double.isNaN(response.getCheckInRate())).isFalse();
    }

    @Test
    void getAnalytics_treatsNullRevenueAsZero() {
        when(eventRepository.countGroupedByStatus()).thenReturn(List.of());
        when(bookingRepository.count()).thenReturn(0L);
        when(bookingRepository.sumConfirmedRevenue()).thenReturn(null);
        when(ticketRepository.countByStatus(any())).thenReturn(0L);
        when(ticketRepository.countByStatusIn(any())).thenReturn(0L);

        PlatformAnalyticsResponse response = adminService.getAnalytics();

        assertThat(response.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private Events pendingEvent(UUID id) {
        return Events.builder()
                .id(id)
                .title("Sample Event")
                .venue("Lagos")
                .status(EventStatus.PENDING_APPROVAL)
                .build();
    }
}
