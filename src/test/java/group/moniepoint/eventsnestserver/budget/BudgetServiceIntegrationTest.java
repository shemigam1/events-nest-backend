package group.moniepoint.eventsnestserver.budget;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.budget.dto.request.AddLineItemRequest;
import group.moniepoint.eventsnestserver.budget.dto.request.CreateBudgetRequest;
import group.moniepoint.eventsnestserver.budget.dto.request.MarkLineItemPaidRequest;
import group.moniepoint.eventsnestserver.budget.dto.response.BudgetSummaryResponse;
import group.moniepoint.eventsnestserver.budget.dto.response.LineItemResponse;
import group.moniepoint.eventsnestserver.budget.model.BudgetCategory;
import group.moniepoint.eventsnestserver.budget.model.LineItemStatus;
import group.moniepoint.eventsnestserver.budget.repository.BudgetLineItemRepository;
import group.moniepoint.eventsnestserver.budget.repository.EventBudgetRepository;
import group.moniepoint.eventsnestserver.budget.service.BudgetService;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.email.repository.EmailJobRepository;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.notifications.service.NotificationServiceImpl;
import group.moniepoint.eventsnestserver.sse.dispatcher.SseDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for BudgetService.
 *
 * Uses H2 in-memory database (JPA create-drop).
 * NotificationServiceImpl and SseDispatcher are mocked because they require
 * Kafka / SSE infrastructure unavailable in the test environment.
 * Redis and WebSocket dependencies are mocked by IntegrationTestConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Budget Service Integration Tests")
@Import(IntegrationTestConfig.class)
class BudgetServiceIntegrationTest {

    @Autowired private BudgetService budgetService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private EventBudgetRepository budgetRepository;
    @Autowired private BudgetLineItemRepository lineItemRepository;

    // Mocked because they depend on Kafka / SSE infrastructure not in tests.
    @MockitoBean private NotificationServiceImpl notificationService;
    @MockitoBean private SseDispatcher sseDispatcher;
    @MockitoBean private EmailJobRepository emailJobRepository;

    private User organizer;
    private User manager;
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

        manager = userRepository.save(User.builder()
                .id("manager00001")
                .firstName("Mike")
                .lastName("Manager")
                .email("mike@test.com")
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
                .title("Tech Summit 2025")
                .description("Annual tech conference")
                .venue("Landmark Event Centre")
                .startTime(LocalDateTime.now().plusDays(30))
                .endTime(LocalDateTime.now().plusDays(30).plusHours(8))
                .status(EventStatus.DRAFT)
                .createdBy(organizer)
                .build());

        membershipRepository.save(EventMembership.builder()
                .events(event).user(organizer).role(EventRole.ORGANIZER).assignedBy(organizer).build());

        membershipRepository.save(EventMembership.builder()
                .events(event).user(manager).role(EventRole.MANAGER).assignedBy(organizer).build());
    }

    // ─── createBudget() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("createBudget()")
    class CreateBudget {

        @Test
        @DisplayName("Organizer can create a budget for their event")
        void organizerCanCreateBudget() {
            EventsNestResponse<BudgetSummaryResponse> response =
                    budgetService.createBudget(event.getId(), budgetRequest("500000.00"), organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Budget created");
            assertThat(response.getData().getTotalBudget()).isEqualByComparingTo("500000.00");
        }

        @Test
        @DisplayName("Manager can create a budget for their event")
        void managerCanCreateBudget() {
            EventsNestResponse<BudgetSummaryResponse> response =
                    budgetService.createBudget(event.getId(), budgetRequest("300000.00"), manager);

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Throws IllegalStateException when budget already exists")
        void throwsWhenBudgetAlreadyExists() {
            budgetService.createBudget(event.getId(), budgetRequest("500000.00"), organizer);

            assertThatThrownBy(() ->
                    budgetService.createBudget(event.getId(), budgetRequest("200000.00"), organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("Throws UnauthorizedException when stranger tries to create a budget")
        void throwsForStranger() {
            assertThatThrownBy(() ->
                    budgetService.createBudget(event.getId(), budgetRequest("100000.00"), stranger))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── addLineItem() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addLineItem()")
    class AddLineItem {

        @Test
        @DisplayName("Organizer can add a line item expense to the budget")
        void organizerCanAddLineItem() {
            budgetService.createBudget(event.getId(), budgetRequest("500000.00"), organizer);

            EventsNestResponse<LineItemResponse> response =
                    budgetService.addLineItem(event.getId(), lineItemRequest("Venue rental", "100000.00"), organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Expense added");
            assertThat(response.getData().getDescription()).isEqualTo("Venue rental");
            assertThat(response.getData().getPlannedAmount()).isEqualByComparingTo("100000.00");
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when no budget exists for the event")
        void throwsWhenNoBudgetExists() {
            assertThatThrownBy(() ->
                    budgetService.addLineItem(event.getId(), lineItemRequest("Venue", "50000.00"), organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws UnauthorizedException when stranger tries to add a line item")
        void throwsForStranger() {
            budgetService.createBudget(event.getId(), budgetRequest("500000.00"), organizer);

            assertThatThrownBy(() ->
                    budgetService.addLineItem(event.getId(), lineItemRequest("Venue", "50000.00"), stranger))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── getSummary() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSummary()")
    class GetSummary {

        @Test
        @DisplayName("Returns correct total and spent amounts after adding line items")
        void returnsSummaryWithCorrectAmounts() {
            budgetService.createBudget(event.getId(), budgetRequest("1000000.00"), organizer);
            budgetService.addLineItem(event.getId(), lineItemRequest("Venue", "200000.00"), organizer);
            budgetService.addLineItem(event.getId(), lineItemRequest("Catering", "150000.00"), organizer);

            BudgetSummaryResponse summary = budgetService.getSummary(event.getId(), organizer);

            assertThat(summary.getTotalBudget()).isEqualByComparingTo("1000000.00");
            assertThat(summary.getLineItems()).hasSize(2);
        }

        @Test
        @DisplayName("Throws UnauthorizedException when non-member requests the summary")
        void throwsForStranger() {
            budgetService.createBudget(event.getId(), budgetRequest("500000.00"), organizer);

            assertThatThrownBy(() -> budgetService.getSummary(event.getId(), stranger))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── markPaid() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("markPaid()")
    class MarkPaid {

        @Test
        @DisplayName("Organizer can mark a PLANNED expense as PAID")
        void organizerCanMarkItemPaid() {
            budgetService.createBudget(event.getId(), budgetRequest("500000.00"), organizer);
            LineItemResponse item =
                    budgetService.addLineItem(event.getId(), lineItemRequest("Venue", "100000.00"), organizer)
                                 .getData();

            MarkLineItemPaidRequest req = new MarkLineItemPaidRequest();
            req.setActualAmount(new BigDecimal("95000.00"));

            EventsNestResponse<LineItemResponse> response =
                    budgetService.markPaid(event.getId(), item.getId(), req, organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getStatus()).isEqualTo(LineItemStatus.PAID);
            assertThat(response.getData().getActualAmount()).isEqualByComparingTo("95000.00");
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CreateBudgetRequest budgetRequest(String amount) {
        CreateBudgetRequest req = new CreateBudgetRequest();
        req.setTotalBudget(new BigDecimal(amount));
        req.setNotes("Integration test budget");
        return req;
    }

    private AddLineItemRequest lineItemRequest(String description, String amount) {
        AddLineItemRequest req = new AddLineItemRequest();
        req.setCategory(BudgetCategory.VENUE);
        req.setDescription(description);
        req.setPlannedAmount(new BigDecimal(amount));
        return req;
    }
}
