package group.moniepoint.eventsnestserver.programme;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventDayRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.programme.ProgrammeItemNotFoundException;
import group.moniepoint.eventsnestserver.exception.programme.ProgrammeNotEnabledException;
import group.moniepoint.eventsnestserver.programme.dto.request.CreateProgrammeItemRequest;
import group.moniepoint.eventsnestserver.programme.model.ProgrammeItem;
import group.moniepoint.eventsnestserver.programme.repository.ProgrammeItemRepository;
import group.moniepoint.eventsnestserver.programme.service.ProgrammeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class ProgrammeServiceTest {

    @Mock private ProgrammeItemRepository itemRepository;
    @Mock private EventRespository eventRepository;
    @Mock private EventConfigRepository configRepository;
    @Mock private EventMembershipRepository membershipRepository;
    @Mock private EventDayRepository dayRepository;

    private ProgrammeServiceImpl programmeService;

    @BeforeEach
    void setUp() {
        programmeService = new ProgrammeServiceImpl(
                itemRepository, eventRepository, configRepository, membershipRepository, dayRepository);
    }

    private User organizer(UUID eventId) {
        User user = User.builder().id("org-1").email("org@test.com").build();
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, "org-1", EventRole.ORGANIZER))
                .thenReturn(true);
        return user;
    }

    private void enableProgramme(UUID eventId) {
        when(configRepository.findByEventId(eventId))
                .thenReturn(Optional.of(EventConfig.builder().programmeEnabled(true).build()));
    }

    @Test
    void addItem_savesAndReturnsResponse() {
        UUID eventId = UUID.randomUUID();
        Events event = Events.builder().id(eventId).title("Conf").build();
        User org = organizer(eventId);
        enableProgramme(eventId);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(itemRepository.save(any())).thenAnswer(inv -> {
            ProgrammeItem item = inv.getArgument(0);
            return ProgrammeItem.builder()
                    .id(UUID.randomUUID()).event(event)
                    .title(item.getTitle()).displayOrder(item.getDisplayOrder())
                    .build();
        });

        CreateProgrammeItemRequest req = new CreateProgrammeItemRequest();
        req.setTitle("Opening Keynote");
        req.setDisplayOrder(1);

        var result = programmeService.addItem(eventId, req, org);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTitle()).isEqualTo("Opening Keynote");
    }

    @Test
    void addItem_throwsWhenProgrammeDisabled() {
        UUID eventId = UUID.randomUUID();
        Events event = Events.builder().id(eventId).build();
        User org = organizer(eventId);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(configRepository.findByEventId(eventId))
                .thenReturn(Optional.of(EventConfig.builder().programmeEnabled(false).build()));

        CreateProgrammeItemRequest req = new CreateProgrammeItemRequest();
        req.setTitle("Talk");

        assertThatThrownBy(() -> programmeService.addItem(eventId, req, org))
                .isInstanceOf(ProgrammeNotEnabledException.class);

        verify(itemRepository, never()).save(any());
    }

    @Test
    void addItem_throwsWhenNotOrganizer() {
        UUID eventId = UUID.randomUUID();
        Events event = Events.builder().id(eventId).build();
        User nonOrg = User.builder().id("other").build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, "other", EventRole.ORGANIZER))
                .thenReturn(false);

        CreateProgrammeItemRequest req = new CreateProgrammeItemRequest();
        req.setTitle("Talk");

        assertThatThrownBy(() -> programmeService.addItem(eventId, req, nonOrg))
                .isInstanceOf(NotEventOrganizerException.class);
    }

    @Test
    void getItems_returnsProgrammeWhenEnabled() {
        UUID eventId = UUID.randomUUID();
        Events event = Events.builder().id(eventId).build();
        enableProgramme(eventId);

        ProgrammeItem item = ProgrammeItem.builder()
                .id(UUID.randomUUID()).event(event).title("Talk").displayOrder(1).build();
        when(itemRepository.findAllByEventIdOrderByDisplayOrderAsc(eventId))
                .thenReturn(List.of(item));

        List<?> items = programmeService.getItems(eventId);

        assertThat(items).hasSize(1);
    }

    @Test
    void getItems_throwsWhenProgrammeDisabled() {
        UUID eventId = UUID.randomUUID();
        when(configRepository.findByEventId(eventId))
                .thenReturn(Optional.of(EventConfig.builder().programmeEnabled(false).build()));

        assertThatThrownBy(() -> programmeService.getItems(eventId))
                .isInstanceOf(ProgrammeNotEnabledException.class);
    }

    @Test
    void deleteItem_throwsWhenItemBelongsToDifferentEvent() {
        UUID eventId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();
        User org = organizer(eventId);
        enableProgramme(eventId);

        Events otherEvent = Events.builder().id(otherEventId).build();
        ProgrammeItem item = ProgrammeItem.builder().id(itemId).event(otherEvent).build();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> programmeService.deleteItem(eventId, itemId, org))
                .isInstanceOf(ProgrammeItemNotFoundException.class);
    }
}
