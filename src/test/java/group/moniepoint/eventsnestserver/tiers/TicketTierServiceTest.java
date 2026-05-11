package group.moniepoint.eventsnestserver.tiers;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.models.EventDay;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventDayRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.exception.event.EventDayNotFoundException;
import group.moniepoint.eventsnestserver.tiers.dto.request.CreateTicketTierRequest;
import group.moniepoint.eventsnestserver.tiers.dto.request.UpdateTicketTierRequest;
import group.moniepoint.eventsnestserver.tiers.dto.response.TicketTierResponse;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import group.moniepoint.eventsnestserver.tiers.service.TicketTierServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketTierServiceTest {

    @Mock
    private TicketTierRepository tierRepository;

    @Mock
    private EventRespository eventRepository;

    @Mock
    private EventMembershipRepository membershipRepository;

    @Mock
    private EventDayRepository dayRepository;

    private TicketTierServiceImpl tierService;

    private User organizer;
    private Events event;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        tierService = new TicketTierServiceImpl(
                new ModelMapper(), tierRepository, eventRepository, membershipRepository, dayRepository);

        organizer = User.builder()
                .id("testuser0001")
                .email("organizer@example.com")
                .firstName("Test")
                .lastName("Organizer")
                .passwordHash("hashed")
                .role(Role.USER)
                .build();

        eventId = UUID.randomUUID();
        event = new Events();
        event.setId(eventId);
        event.setTitle("Tech Summit");
        event.setVenue("Lagos");
        event.setStartTime(LocalDateTime.of(2026, 8, 1, 9, 0));
        event.setEndTime(LocalDateTime.of(2026, 8, 1, 17, 0));
        event.setStatus(EventStatus.DRAFT);
    }

    // ─── createTier ──────────────────────────────────────────────────────────────

    @Test
    void createTierPersistsWithComputedTotalCapacityAndAvailableCapacity() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(true);
        when(tierRepository.saveAndFlush(any(TicketTier.class))).thenAnswer(inv -> {
            TicketTier t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        CreateTicketTierRequest request = new CreateTicketTierRequest();
        request.setName("VIP Front Row");
        request.setPrice(new BigDecimal("250.00"));
        request.setRowPrefix("VIP");
        request.setRowCount(5);
        request.setSeatsPerRow(10);

        EventsNestResponse<TicketTierResponse> response = tierService.createTier(eventId, request, organizer);

        ArgumentCaptor<TicketTier> captor = ArgumentCaptor.forClass(TicketTier.class);
        verify(tierRepository).saveAndFlush(captor.capture());
        TicketTier saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("VIP Front Row");
        assertThat(saved.getPrice()).isEqualByComparingTo("250.00");
        assertThat(saved.getRowPrefix()).isEqualTo("VIP");
        assertThat(saved.getRowCount()).isEqualTo(5);
        assertThat(saved.getSeatsPerRow()).isEqualTo(10);
        assertThat(saved.getTotalCapacity()).isEqualTo(50);
        assertThat(saved.getAvailableCapacity()).isEqualTo(50);
        assertThat(saved.getEvent()).isEqualTo(event);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Ticket tier created successfully");
        assertThat(response.getData().getEventId()).isEqualTo(eventId);
        assertThat(response.getData().getTotalCapacity()).isEqualTo(50);
    }

    @Test
    void createTierThrowsResourceNotFoundWhenEventDoesNotExist() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        CreateTicketTierRequest request = validCreateRequest();

        assertThatThrownBy(() -> tierService.createTier(eventId, request, organizer))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("event not found");

        verify(tierRepository, never()).save(any());
    }

    @Test
    void createTierThrowsUnauthorizedWhenUserIsNotOrganizer() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(false);

        assertThatThrownBy(() -> tierService.createTier(eventId, validCreateRequest(), organizer))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("only the event organizer can perform this action");

        verify(tierRepository, never()).save(any());
    }

    @Test
    void createTierWithEventDayIdPersistsDaySpecificTier() {
        UUID dayId = UUID.randomUUID();
        EventDay day = EventDay.builder().id(dayId).event(event).dayNumber(2).build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(true);
        when(dayRepository.findByIdAndEventId(dayId, eventId)).thenReturn(Optional.of(day));
        when(tierRepository.saveAndFlush(any(TicketTier.class))).thenAnswer(inv -> {
            TicketTier t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        CreateTicketTierRequest request = validCreateRequest();
        request.setEventDayId(dayId);

        EventsNestResponse<TicketTierResponse> response = tierService.createTier(eventId, request, organizer);

        ArgumentCaptor<TicketTier> captor = ArgumentCaptor.forClass(TicketTier.class);
        verify(tierRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getEventDay()).isEqualTo(day);
        assertThat(response.getData().getEventDayId()).isEqualTo(dayId);
    }

    @Test
    void createTierWithEventDayIdFromDifferentEventThrows() {
        UUID dayId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(true);
        when(dayRepository.findByIdAndEventId(dayId, eventId)).thenReturn(Optional.empty());

        CreateTicketTierRequest request = validCreateRequest();
        request.setEventDayId(dayId);

        assertThatThrownBy(() -> tierService.createTier(eventId, request, organizer))
                .isInstanceOf(EventDayNotFoundException.class);

        verify(tierRepository, never()).saveAndFlush(any());
    }

    // ─── getTiersByEvent ─────────────────────────────────────────────────────────

    @Test
    void getTiersByEventReturnsMappedTiers() {
        when(eventRepository.existsById(eventId)).thenReturn(true);
        TicketTier vip = tierFor(event, "VIP", 50);
        TicketTier general = tierFor(event, "General", 200);
        when(tierRepository.findAllByEventId(eventId)).thenReturn(List.of(vip, general));

        List<TicketTierResponse> result = tierService.getTiersByEvent(eventId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TicketTierResponse::getName).containsExactly("VIP", "General");
        assertThat(result).extracting(TicketTierResponse::getEventId).containsOnly(eventId);
    }

    @Test
    void getTiersByEventThrowsResourceNotFoundWhenEventDoesNotExist() {
        when(eventRepository.existsById(eventId)).thenReturn(false);

        assertThatThrownBy(() -> tierService.getTiersByEvent(eventId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("event not found");
    }

    // ─── updateTier ──────────────────────────────────────────────────────────────

    @Test
    void updateTierAppliesOnlyProvidedFields() {
        UUID tierId = UUID.randomUUID();
        TicketTier existing = tierFor(event, "VIP", 50);
        existing.setId(tierId);
        existing.setPrice(new BigDecimal("100.00"));

        when(tierRepository.findById(tierId)).thenReturn(Optional.of(existing));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(true);
        when(tierRepository.saveAndFlush(any(TicketTier.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTicketTierRequest request = new UpdateTicketTierRequest();
        request.setPrice(new BigDecimal("150.00"));

        EventsNestResponse<TicketTierResponse> response =
                tierService.updateTier(eventId, tierId, request, organizer);

        ArgumentCaptor<TicketTier> captor = ArgumentCaptor.forClass(TicketTier.class);
        verify(tierRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getName()).isEqualTo("VIP"); // unchanged
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("150.00"); // updated

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Ticket tier updated successfully");
    }

    @Test
    void updateTierThrowsResourceNotFoundWhenTierBelongsToDifferentEvent() {
        UUID tierId = UUID.randomUUID();
        Events otherEvent = new Events();
        otherEvent.setId(UUID.randomUUID());
        TicketTier tierOnOtherEvent = tierFor(otherEvent, "VIP", 50);
        tierOnOtherEvent.setId(tierId);

        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tierOnOtherEvent));

        assertThatThrownBy(() -> tierService.updateTier(eventId, tierId, new UpdateTicketTierRequest(), organizer))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("ticket tier not found");

        verify(tierRepository, never()).save(any());
    }

    @Test
    void updateTierThrowsUnauthorizedWhenUserIsNotOrganizer() {
        UUID tierId = UUID.randomUUID();
        TicketTier existing = tierFor(event, "VIP", 50);
        existing.setId(tierId);

        when(tierRepository.findById(tierId)).thenReturn(Optional.of(existing));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(false);

        assertThatThrownBy(() -> tierService.updateTier(eventId, tierId, new UpdateTicketTierRequest(), organizer))
                .isInstanceOf(UnauthorizedException.class);

        verify(tierRepository, never()).save(any());
    }

    // ─── deleteTier ──────────────────────────────────────────────────────────────

    @Test
    void deleteTierSucceedsWhenOrganizer() {
        UUID tierId = UUID.randomUUID();
        TicketTier existing = tierFor(event, "VIP", 50);
        existing.setId(tierId);

        when(tierRepository.findById(tierId)).thenReturn(Optional.of(existing));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(true);

        tierService.deleteTier(eventId, tierId, organizer);

        verify(tierRepository).delete(existing);
    }

    @Test
    void deleteTierThrowsUnauthorizedWhenUserIsNotOrganizer() {
        UUID tierId = UUID.randomUUID();
        TicketTier existing = tierFor(event, "VIP", 50);
        existing.setId(tierId);

        when(tierRepository.findById(tierId)).thenReturn(Optional.of(existing));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(false);

        assertThatThrownBy(() -> tierService.deleteTier(eventId, tierId, organizer))
                .isInstanceOf(UnauthorizedException.class);

        verify(tierRepository, never()).delete(any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private CreateTicketTierRequest validCreateRequest() {
        CreateTicketTierRequest request = new CreateTicketTierRequest();
        request.setName("VIP");
        request.setPrice(new BigDecimal("100.00"));
        request.setRowPrefix("VIP");
        request.setRowCount(5);
        request.setSeatsPerRow(10);
        return request;
    }

    private TicketTier tierFor(Events evt, String name, int capacity) {
        return TicketTier.builder()
                .event(evt)
                .name(name)
                .price(new BigDecimal("100.00"))
                .rowPrefix("X")
                .rowCount(1)
                .seatsPerRow(capacity)
                .totalCapacity(capacity)
                .availableCapacity(capacity)
                .build();
    }
}
