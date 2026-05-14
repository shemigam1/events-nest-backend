package group.moniepoint.eventsnestserver.programme;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.programme.ProgrammeNotEnabledException;
import group.moniepoint.eventsnestserver.programme.dto.request.CreateProgrammeItemRequest;
import group.moniepoint.eventsnestserver.programme.dto.request.UpdateProgrammeItemRequest;
import group.moniepoint.eventsnestserver.programme.dto.response.ProgrammeItemResponse;
import group.moniepoint.eventsnestserver.programme.repository.ProgrammeItemRepository;
import group.moniepoint.eventsnestserver.programme.service.ProgrammeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for ProgrammeService.
 *
 * Uses H2 in-memory database (JPA create-drop).
 * Redis and WebSocket dependencies are mocked by IntegrationTestConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Programme Service Integration Tests")
@Import(IntegrationTestConfig.class)
class ProgrammeServiceIntegrationTest {

    @Autowired private ProgrammeService programmeService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private EventConfigRepository configRepository;
    @Autowired private ProgrammeItemRepository itemRepository;

    private User organizer;
    private User stranger;
    private Events event;

    @BeforeEach
    void seed() {
        organizer = userRepository.save(User.builder()
                .id("organizer001")
                .firstName("Eve")
                .lastName("Organizer")
                .email("eve@test.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .build());

        stranger = userRepository.save(User.builder()
                .id("stranger0001")
                .firstName("Sam")
                .lastName("Stranger")
                .email("sam@test.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .build());

        event = eventRepository.save(Events.builder()
                .title("DevFest 2025")
                .description("Developer festival")
                .venue("Landmark Event Centre, Lagos")
                .startTime(LocalDateTime.now().plusDays(30))
                .endTime(LocalDateTime.now().plusDays(30).plusHours(10))
                .status(EventStatus.PUBLISHED)
                .createdBy(organizer)
                .build());

        membershipRepository.save(EventMembership.builder()
                .events(event)
                .user(organizer)
                .role(EventRole.ORGANIZER)
                .assignedBy(organizer)
                .build());

        // Enable programme in the event config
        configRepository.save(EventConfig.builder()
                .event(event)
                .programmeEnabled(true)
                .build());
    }

    // ─── addItem() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addItem()")
    class AddItem {

        @Test
        @DisplayName("Organizer can add a programme item")
        void organizerCanAddProgrammeItem() {
            EventsNestResponse<ProgrammeItemResponse> response =
                    programmeService.addItem(event.getId(), itemRequest("Keynote Address", 1), organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Programme item added");
            assertThat(response.getData().getTitle()).isEqualTo("Keynote Address");
            assertThat(response.getData().getDisplayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer tries to add an item")
        void throwsForNonOrganizer() {
            assertThatThrownBy(() ->
                    programmeService.addItem(event.getId(), itemRequest("Talk", 1), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Throws ProgrammeNotEnabledException when programme is disabled in config")
        void throwsWhenProgrammeDisabled() {
            configRepository.findByEventId(event.getId()).ifPresent(c -> {
                c.setProgrammeEnabled(false);
                configRepository.save(c);
            });

            assertThatThrownBy(() ->
                    programmeService.addItem(event.getId(), itemRequest("Talk", 1), organizer))
                    .isInstanceOf(ProgrammeNotEnabledException.class);
        }
    }

    // ─── getItems() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getItems()")
    class GetItems {

        @Test
        @DisplayName("Returns all programme items ordered by displayOrder ascending")
        void returnsItemsInOrder() {
            programmeService.addItem(event.getId(), itemRequest("Workshop A", 3), organizer);
            programmeService.addItem(event.getId(), itemRequest("Keynote", 1), organizer);
            programmeService.addItem(event.getId(), itemRequest("Panel", 2), organizer);

            List<ProgrammeItemResponse> items = programmeService.getItems(event.getId());

            assertThat(items).hasSize(3);
            assertThat(items).extracting(ProgrammeItemResponse::getDisplayOrder)
                    .containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("Returns empty list when no items have been added")
        void returnsEmptyWhenNoItems() {
            assertThat(programmeService.getItems(event.getId())).isEmpty();
        }
    }

    // ─── updateItem() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateItem()")
    class UpdateItem {

        @Test
        @DisplayName("Organizer can update an existing programme item")
        void organizerCanUpdateItem() {
            ProgrammeItemResponse item =
                    programmeService.addItem(event.getId(), itemRequest("Old Title", 1), organizer).getData();

            UpdateProgrammeItemRequest update = new UpdateProgrammeItemRequest();
            update.setTitle("New Title");
            update.setSpeakerName("Dr. Ada");

            ProgrammeItemResponse updated =
                    programmeService.updateItem(event.getId(), item.getId(), update, organizer).getData();

            assertThat(updated.getTitle()).isEqualTo("New Title");
            assertThat(updated.getSpeakerName()).isEqualTo("Dr. Ada");
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer tries to update")
        void throwsForNonOrganizer() {
            ProgrammeItemResponse item =
                    programmeService.addItem(event.getId(), itemRequest("Talk", 1), organizer).getData();

            assertThatThrownBy(() ->
                    programmeService.updateItem(event.getId(), item.getId(), new UpdateProgrammeItemRequest(), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }
    }

    // ─── deleteItem() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteItem()")
    class DeleteItem {

        @Test
        @DisplayName("Organizer can delete a programme item")
        void organizerCanDeleteItem() {
            ProgrammeItemResponse item =
                    programmeService.addItem(event.getId(), itemRequest("Talk", 1), organizer).getData();

            programmeService.deleteItem(event.getId(), item.getId(), organizer);

            assertThat(itemRepository.findById(item.getId())).isEmpty();
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer tries to delete")
        void throwsForNonOrganizer() {
            ProgrammeItemResponse item =
                    programmeService.addItem(event.getId(), itemRequest("Talk", 1), organizer).getData();

            assertThatThrownBy(() -> programmeService.deleteItem(event.getId(), item.getId(), stranger))
                    .isInstanceOf(NotEventOrganizerException.class);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CreateProgrammeItemRequest itemRequest(String title, int order) {
        CreateProgrammeItemRequest req = new CreateProgrammeItemRequest();
        req.setTitle(title);
        req.setDisplayOrder(order);
        req.setStartTime(LocalDateTime.now().plusDays(30).plusHours(order));
        req.setEndTime(LocalDateTime.now().plusDays(30).plusHours(order + 1));
        return req;
    }
}
