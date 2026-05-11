package group.moniepoint.eventsnestserver.events;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventConfigRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventConfigResponse;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.service.EventConfigServiceImpl;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.event.EventConfigNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventConfigServiceTest {

    @Mock
    private EventConfigRepository configRepository;

    @Mock
    private EventMembershipRepository membershipRepository;

    private EventConfigServiceImpl configService;

    private Events event;
    private User organizer;

    @BeforeEach
    void setUp() {
        configService = new EventConfigServiceImpl(configRepository, membershipRepository);

        event = Events.builder()
                .id(UUID.randomUUID())
                .title("Test Event")
                .build();

        organizer = User.builder()
                .id("organizer001")
                .email("organizer@example.com")
                .firstName("Org")
                .lastName("Anizer")
                .passwordHash("hashed")
                .role(Role.USER)
                .build();
    }

    // ─── createDefaultsFor ──────────────────────────────────────────────────────

    @Test
    void createDefaultsForPersistsConfigWithSafeDefaults() {
        when(configRepository.saveAndFlush(any(EventConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EventConfig result = configService.createDefaultsFor(event);

        ArgumentCaptor<EventConfig> captor = ArgumentCaptor.forClass(EventConfig.class);
        verify(configRepository).saveAndFlush(captor.capture());
        EventConfig persisted = captor.getValue();

        assertThat(persisted.getEvent()).isEqualTo(event);
        assertThat(persisted.isTicketingEnabled()).isTrue();
        assertThat(persisted.isGuestListEnabled()).isFalse();
        assertThat(persisted.isProgrammeEnabled()).isFalse();
        assertThat(persisted.isRatingsEnabled()).isFalse();
        assertThat(result).isSameAs(persisted);
    }

    // ─── getByEventId ───────────────────────────────────────────────────────────

    @Test
    void getByEventIdReturnsConfigFlags() {
        EventConfig config = EventConfig.builder()
                .id(UUID.randomUUID())
                .event(event)
                .ticketingEnabled(true)
                .guestListEnabled(true)
                .programmeEnabled(false)
                .ratingsEnabled(true)
                .build();
        when(configRepository.findByEventId(event.getId())).thenReturn(Optional.of(config));

        EventConfigResponse response = configService.getByEventId(event.getId());

        assertThat(response.getEventId()).isEqualTo(event.getId());
        assertThat(response.isTicketingEnabled()).isTrue();
        assertThat(response.isGuestListEnabled()).isTrue();
        assertThat(response.isProgrammeEnabled()).isFalse();
        assertThat(response.isRatingsEnabled()).isTrue();
    }

    @Test
    void getByEventIdThrowsWhenConfigMissing() {
        when(configRepository.findByEventId(event.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configService.getByEventId(event.getId()))
                .isInstanceOf(EventConfigNotFoundException.class);
    }

    // ─── updateByEventId ────────────────────────────────────────────────────────

    @Test
    void updateByEventIdAppliesProvidedFieldsAndLeavesNullsUntouched() {
        EventConfig existing = EventConfig.builder()
                .id(UUID.randomUUID())
                .event(event)
                .ticketingEnabled(true)
                .guestListEnabled(false)
                .programmeEnabled(false)
                .ratingsEnabled(false)
                .build();
        when(configRepository.findByEventId(event.getId())).thenReturn(Optional.of(existing));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                event.getId(), organizer.getId(), EventRole.ORGANIZER)).thenReturn(true);
        when(configRepository.saveAndFlush(any(EventConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpdateEventConfigRequest request = new UpdateEventConfigRequest();
        request.setGuestListEnabled(true);
        request.setRatingsEnabled(true);
        // ticketingEnabled and programmeEnabled stay null — should not change

        EventsNestResponse<EventConfigResponse> result =
                configService.updateByEventId(event.getId(), request, organizer);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().isTicketingEnabled()).isTrue();   // unchanged
        assertThat(result.getData().isGuestListEnabled()).isTrue();   // toggled on
        assertThat(result.getData().isProgrammeEnabled()).isFalse();  // unchanged
        assertThat(result.getData().isRatingsEnabled()).isTrue();     // toggled on
    }

    @Test
    void updateByEventIdThrowsWhenCallerIsNotOrganizer() {
        EventConfig existing = EventConfig.builder()
                .id(UUID.randomUUID())
                .event(event)
                .build();
        when(configRepository.findByEventId(event.getId())).thenReturn(Optional.of(existing));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                event.getId(), organizer.getId(), EventRole.ORGANIZER)).thenReturn(false);

        UpdateEventConfigRequest request = new UpdateEventConfigRequest();
        request.setGuestListEnabled(true);

        assertThatThrownBy(() -> configService.updateByEventId(event.getId(), request, organizer))
                .isInstanceOf(NotEventOrganizerException.class);
    }

    @Test
    void updateByEventIdThrowsWhenConfigMissing() {
        when(configRepository.findByEventId(event.getId())).thenReturn(Optional.empty());

        UpdateEventConfigRequest request = new UpdateEventConfigRequest();
        request.setGuestListEnabled(true);

        assertThatThrownBy(() -> configService.updateByEventId(event.getId(), request, organizer))
                .isInstanceOf(EventConfigNotFoundException.class);
    }
}
