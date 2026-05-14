package group.moniepoint.eventsnestserver.budget;

import com.fasterxml.jackson.databind.ObjectMapper;
import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.bookings.repository.BookingRepository;
import group.moniepoint.eventsnestserver.budget.dto.request.AddLineItemRequest;
import group.moniepoint.eventsnestserver.budget.dto.request.CreateBudgetRequest;
import group.moniepoint.eventsnestserver.budget.dto.request.MarkLineItemPaidRequest;
import group.moniepoint.eventsnestserver.budget.dto.response.BudgetSummaryResponse;
import group.moniepoint.eventsnestserver.budget.dto.response.LineItemResponse;
import group.moniepoint.eventsnestserver.budget.model.BudgetCategory;
import group.moniepoint.eventsnestserver.budget.model.BudgetLineItem;
import group.moniepoint.eventsnestserver.budget.model.EventBudget;
import group.moniepoint.eventsnestserver.budget.model.LineItemStatus;
import group.moniepoint.eventsnestserver.budget.repository.BudgetLineItemRepository;
import group.moniepoint.eventsnestserver.budget.repository.EventBudgetRepository;
import group.moniepoint.eventsnestserver.budget.service.BudgetServiceImpl;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.email.repository.EmailJobRepository;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.notifications.service.NotificationServiceImpl;
import group.moniepoint.eventsnestserver.sse.dispatcher.SseDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BudgetServiceImpl.
 *
 * All dependencies are mocked — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetService Unit Tests")
class BudgetServiceTest {

    @Mock private EventBudgetRepository budgetRepository;
    @Mock private BudgetLineItemRepository lineItemRepository;
    @Mock private EventRespository eventRepository;
    @Mock private EventMembershipRepository membershipRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private NotificationServiceImpl notificationService;
    @Mock private SseDispatcher sseDispatcher;
    @Mock private EmailJobRepository emailJobRepository;
    @Mock private ObjectMapper objectMapper;

    private BudgetServiceImpl budgetService;

    private User organizer;
    private User manager;
    private User stranger;
    private Events event;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetServiceImpl(
                budgetRepository, lineItemRepository, eventRepository,
                membershipRepository, bookingRepository, notificationService,
                sseDispatcher, emailJobRepository, objectMapper);

        organizer = User.builder()
                .id("organizer001")
                .firstName("Eve")
                .lastName("Organizer")
                .email("eve@test.com")
                .role(Role.USER)
                .build();

        manager = User.builder()
                .id("manager0001")
                .firstName("Mike")
                .lastName("Manager")
                .email("mike@test.com")
                .role(Role.USER)
                .build();

        stranger = User.builder()
                .id("stranger0001")
                .firstName("Sam")
                .lastName("Stranger")
                .email("sam@test.com")
                .role(Role.USER)
                .build();

        eventId = UUID.randomUUID();
        event = Events.builder()
                .id(eventId)
                .title("Tech Summit 2025")
                .createdBy(organizer)
                .build();
    }

    // ─── createBudget() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("createBudget()")
    class CreateBudget {

        @Test
        @DisplayName("Organizer can create a budget and it is persisted")
        void organizerCanCreateBudget() {
            stubIsOrganizer(organizer);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(budgetRepository.existsByEventId(eventId)).thenReturn(false);
            when(budgetRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(lineItemRepository.findAllByBudgetIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
            when(bookingRepository.sumConfirmedRevenueByEventId(eventId)).thenReturn(BigDecimal.ZERO);

            EventsNestResponse<BudgetSummaryResponse> response =
                    budgetService.createBudget(eventId, budgetRequest("500000.00"), organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Budget created");
            assertThat(response.getData().getTotalBudget()).isEqualByComparingTo("500000.00");

            ArgumentCaptor<EventBudget> captor = ArgumentCaptor.forClass(EventBudget.class);
            verify(budgetRepository).save(captor.capture());
            assertThat(captor.getValue().getTotalBudget()).isEqualByComparingTo("500000.00");
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(organizer);
        }

        @Test
        @DisplayName("Manager can also create a budget")
        void managerCanCreateBudget() {
            stubIsManager(manager);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(budgetRepository.existsByEventId(eventId)).thenReturn(false);
            when(budgetRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(lineItemRepository.findAllByBudgetIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
            when(bookingRepository.sumConfirmedRevenueByEventId(eventId)).thenReturn(BigDecimal.ZERO);

            EventsNestResponse<BudgetSummaryResponse> response =
                    budgetService.createBudget(eventId, budgetRequest("300000.00"), manager);

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Throws IllegalStateException when a budget already exists for the event")
        void throwsWhenBudgetAlreadyExists() {
            stubIsOrganizer(organizer);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(budgetRepository.existsByEventId(eventId)).thenReturn(true);

            assertThatThrownBy(() -> budgetService.createBudget(eventId, budgetRequest("500000.00"), organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already exists");

            verify(budgetRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws UnauthorizedException when a stranger tries to create a budget")
        void throwsForStranger() {
            stubIsNeitherOrganizerNorManager(stranger);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> budgetService.createBudget(eventId, budgetRequest("500000.00"), stranger))
                    .isInstanceOf(UnauthorizedException.class);

            verify(budgetRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws EventNotFoundException when event does not exist")
        void throwsWhenEventDoesNotExist() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> budgetService.createBudget(eventId, budgetRequest("500000.00"), organizer))
                    .isInstanceOf(EventNotFoundException.class);
        }
    }

    // ─── updateBudget() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateBudget()")
    class UpdateBudget {

        @Test
        @DisplayName("Organizer can update the total budget and notes")
        void organizerCanUpdateBudget() {
            EventBudget existing = budget("500000.00");
            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
            when(budgetRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(lineItemRepository.findAllByBudgetIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
            when(bookingRepository.sumConfirmedRevenueByEventId(eventId)).thenReturn(BigDecimal.ZERO);

            CreateBudgetRequest req = new CreateBudgetRequest();
            req.setTotalBudget(new BigDecimal("800000.00"));
            req.setNotes("Revised budget");

            EventsNestResponse<BudgetSummaryResponse> response =
                    budgetService.updateBudget(eventId, req, organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Budget updated");
            assertThat(existing.getTotalBudget()).isEqualByComparingTo("800000.00");
            assertThat(existing.getNotes()).isEqualTo("Revised budget");
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when no budget exists for the event")
        void throwsWhenNoBudgetExists() {
            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> budgetService.updateBudget(eventId, budgetRequest("200000.00"), organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws UnauthorizedException when a stranger tries to update the budget")
        void throwsForStranger() {
            stubIsNeitherOrganizerNorManager(stranger);

            assertThatThrownBy(() -> budgetService.updateBudget(eventId, budgetRequest("200000.00"), stranger))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── getSummary() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSummary()")
    class GetSummary {

        @Test
        @DisplayName("Returns summary with correct totals computed from line items")
        void returnsCorrectSummary() {
            EventBudget existing = budget("1000000.00");
            BudgetLineItem plannedItem  = lineItem("200000.00", null, LineItemStatus.PLANNED);
            BudgetLineItem paidItem     = lineItem("150000.00", "140000.00", LineItemStatus.PAID);

            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
            when(lineItemRepository.findAllByBudgetIdOrderByCreatedAtDesc(any()))
                    .thenReturn(List.of(plannedItem, paidItem));
            when(bookingRepository.sumConfirmedRevenueByEventId(eventId))
                    .thenReturn(new BigDecimal("500000.00"));

            BudgetSummaryResponse summary = budgetService.getSummary(eventId, organizer);

            assertThat(summary.getTotalBudget()).isEqualByComparingTo("1000000.00");
            assertThat(summary.getTotalPlanned()).isEqualByComparingTo("350000.00");  // 200k + 150k
            assertThat(summary.getTotalActualSpend()).isEqualByComparingTo("140000.00"); // only PAID
            assertThat(summary.getRemainingBudget()).isEqualByComparingTo("860000.00"); // 1000k - 140k
            assertThat(summary.getTotalRevenue()).isEqualByComparingTo("500000.00");
            assertThat(summary.getNetProfit()).isEqualByComparingTo("360000.00"); // 500k - 140k
            assertThat(summary.getLineItems()).hasSize(2);
        }

        @Test
        @DisplayName("spendPercent is 0 when no PAID line items exist")
        void spendPercentIsZeroWithNoPayments() {
            EventBudget existing = budget("1000000.00");
            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
            when(lineItemRepository.findAllByBudgetIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
            when(bookingRepository.sumConfirmedRevenueByEventId(eventId)).thenReturn(BigDecimal.ZERO);

            BudgetSummaryResponse summary = budgetService.getSummary(eventId, organizer);

            assertThat(summary.getSpendPercent()).isEqualTo(0.0);
            assertThat(summary.isThresholdAlertTriggered()).isFalse();
        }

        @Test
        @DisplayName("thresholdAlertTriggered is true when actual spend exceeds 80% of budget")
        void thresholdAlertTriggeredWhenSpendExceeds80Percent() {
            EventBudget existing = budget("100000.00");
            BudgetLineItem paidItem = lineItem("85000.00", "85000.00", LineItemStatus.PAID);

            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
            when(lineItemRepository.findAllByBudgetIdOrderByCreatedAtDesc(any())).thenReturn(List.of(paidItem));
            when(bookingRepository.sumConfirmedRevenueByEventId(eventId)).thenReturn(BigDecimal.ZERO);

            BudgetSummaryResponse summary = budgetService.getSummary(eventId, organizer);

            assertThat(summary.getSpendPercent()).isGreaterThanOrEqualTo(80.0);
            assertThat(summary.isThresholdAlertTriggered()).isTrue();
        }

        @Test
        @DisplayName("Throws UnauthorizedException when stranger requests summary")
        void throwsForStranger() {
            stubIsNeitherOrganizerNorManager(stranger);

            assertThatThrownBy(() -> budgetService.getSummary(eventId, stranger))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── addLineItem() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addLineItem()")
    class AddLineItem {

        @Test
        @DisplayName("Organizer can add a PLANNED line item expense")
        void organizerCanAddLineItem() {
            EventBudget existing = budget("500000.00");
            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
            when(lineItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            AddLineItemRequest req = lineItemRequest("Venue rental", "100000.00");

            EventsNestResponse<LineItemResponse> response =
                    budgetService.addLineItem(eventId, req, organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Expense added");
            assertThat(response.getData().getDescription()).isEqualTo("Venue rental");
            assertThat(response.getData().getPlannedAmount()).isEqualByComparingTo("100000.00");
            assertThat(response.getData().getStatus()).isEqualTo(LineItemStatus.PLANNED);
        }

        @Test
        @DisplayName("Line item is saved with PLANNED status and linked to the budget")
        void lineItemSavedWithPlannedStatus() {
            EventBudget existing = budget("500000.00");
            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
            when(lineItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            budgetService.addLineItem(eventId, lineItemRequest("Catering", "80000.00"), organizer);

            ArgumentCaptor<BudgetLineItem> captor = ArgumentCaptor.forClass(BudgetLineItem.class);
            verify(lineItemRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(LineItemStatus.PLANNED);
            assertThat(captor.getValue().getBudget()).isEqualTo(existing);
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(organizer);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when no budget exists")
        void throwsWhenNoBudgetExists() {
            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> budgetService.addLineItem(eventId, lineItemRequest("Venue", "50000.00"), organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws UnauthorizedException when stranger tries to add an item")
        void throwsForStranger() {
            stubIsNeitherOrganizerNorManager(stranger);

            assertThatThrownBy(() -> budgetService.addLineItem(eventId, lineItemRequest("Venue", "50000.00"), stranger))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── markPaid() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("markPaid()")
    class MarkPaid {

        @Test
        @DisplayName("Organizer can mark a PLANNED expense as PAID with actual amount")
        void organizerCanMarkItemPaid() {
            EventBudget existing = budget("500000.00");
            UUID itemId = UUID.randomUUID();
            BudgetLineItem item = lineItem("100000.00", null, LineItemStatus.PLANNED);
            item.setId(itemId);

            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
            when(lineItemRepository.findByIdAndBudgetId(itemId, existing.getId())).thenReturn(Optional.of(item));
            when(lineItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(budgetRepository.sumActualSpend(existing.getId())).thenReturn(new BigDecimal("90000.00"));

            MarkLineItemPaidRequest req = new MarkLineItemPaidRequest();
            req.setActualAmount(new BigDecimal("90000.00"));

            EventsNestResponse<LineItemResponse> response =
                    budgetService.markPaid(eventId, itemId, req, organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Expense marked as paid");
            assertThat(item.getStatus()).isEqualTo(LineItemStatus.PAID);
            assertThat(item.getActualAmount()).isEqualByComparingTo("90000.00");
            assertThat(item.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("Fires a budget alert notification when actual spend crosses 80% of budget")
        void firesBudgetAlertAtThreshold() {
            EventBudget existing = budget("100000.00");
            UUID itemId = UUID.randomUUID();
            BudgetLineItem item = lineItem("85000.00", null, LineItemStatus.PLANNED);
            item.setId(itemId);

            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
            when(lineItemRepository.findByIdAndBudgetId(itemId, existing.getId())).thenReturn(Optional.of(item));
            when(lineItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(budgetRepository.sumActualSpend(existing.getId())).thenReturn(new BigDecimal("85000.00")); // 85%
            when(notificationService.createIfAbsent(any(), any(), any(), any(), any())).thenReturn(true);

            MarkLineItemPaidRequest req = new MarkLineItemPaidRequest();
            req.setActualAmount(new BigDecimal("85000.00"));

            budgetService.markPaid(eventId, itemId, req, organizer);

            verify(notificationService).createIfAbsent(
                    eq(organizer.getId()), any(), any(), any(), any());
            verify(sseDispatcher).onBudgetThresholdReached(eq(organizer.getId()), any());
        }

        @Test
        @DisplayName("Does not fire a second alert when notification was already created")
        void doesNotFireDuplicateAlert() {
            EventBudget existing = budget("100000.00");
            UUID itemId = UUID.randomUUID();
            BudgetLineItem item = lineItem("85000.00", null, LineItemStatus.PLANNED);
            item.setId(itemId);

            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
            when(lineItemRepository.findByIdAndBudgetId(itemId, existing.getId())).thenReturn(Optional.of(item));
            when(lineItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(budgetRepository.sumActualSpend(existing.getId())).thenReturn(new BigDecimal("85000.00"));
            when(notificationService.createIfAbsent(any(), any(), any(), any(), any())).thenReturn(false); // already inserted

            MarkLineItemPaidRequest req = new MarkLineItemPaidRequest();
            req.setActualAmount(new BigDecimal("85000.00"));

            budgetService.markPaid(eventId, itemId, req, organizer);

            verify(sseDispatcher, never()).onBudgetThresholdReached(any(), any());
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when line item does not belong to the budget")
        void throwsWhenLineItemNotFound() {
            EventBudget existing = budget("500000.00");
            UUID itemId = UUID.randomUUID();

            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
            when(lineItemRepository.findByIdAndBudgetId(itemId, existing.getId())).thenReturn(Optional.empty());

            MarkLineItemPaidRequest req = new MarkLineItemPaidRequest();
            req.setActualAmount(new BigDecimal("50000.00"));

            assertThatThrownBy(() -> budgetService.markPaid(eventId, itemId, req, organizer))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Line item not found");
        }
    }

    // ─── deleteLineItem() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteLineItem()")
    class DeleteLineItem {

        @Test
        @DisplayName("Organizer can delete a PLANNED line item")
        void organizerCanDeletePlannedItem() {
            EventBudget existing = budget("500000.00");
            UUID itemId = UUID.randomUUID();
            BudgetLineItem item = lineItem("50000.00", null, LineItemStatus.PLANNED);

            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
            when(lineItemRepository.findByIdAndBudgetId(itemId, existing.getId())).thenReturn(Optional.of(item));

            EventsNestResponse<Void> response = budgetService.deleteLineItem(eventId, itemId, organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Line item deleted");
            verify(lineItemRepository).delete(item);
        }

        @Test
        @DisplayName("Throws IllegalStateException when trying to delete a PAID expense")
        void throwsWhenDeletingPaidItem() {
            EventBudget existing = budget("500000.00");
            UUID itemId = UUID.randomUUID();
            BudgetLineItem paidItem = lineItem("50000.00", "48000.00", LineItemStatus.PAID);

            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
            when(lineItemRepository.findByIdAndBudgetId(itemId, existing.getId())).thenReturn(Optional.of(paidItem));

            assertThatThrownBy(() -> budgetService.deleteLineItem(eventId, itemId, organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Paid expenses cannot be deleted");

            verify(lineItemRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Throws UnauthorizedException when stranger tries to delete a line item")
        void throwsForStranger() {
            stubIsNeitherOrganizerNorManager(stranger);

            assertThatThrownBy(() -> budgetService.deleteLineItem(eventId, UUID.randomUUID(), stranger))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when line item does not exist")
        void throwsWhenLineItemNotFound() {
            EventBudget existing = budget("500000.00");
            UUID itemId = UUID.randomUUID();

            stubIsOrganizer(organizer);
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
            when(lineItemRepository.findByIdAndBudgetId(itemId, existing.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> budgetService.deleteLineItem(eventId, itemId, organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── helper stubs ─────────────────────────────────────────────────────────

    /** Makes the membership check pass for ORGANIZER role only. */
    private void stubIsOrganizer(User user) {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER))
                .thenReturn(true);
    }

    /** Makes the membership check pass for MANAGER role only. */
    private void stubIsManager(User user) {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER))
                .thenReturn(false);
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.MANAGER))
                .thenReturn(true);
    }

    /** Makes the membership check fail for both ORGANIZER and MANAGER. */
    private void stubIsNeitherOrganizerNorManager(User user) {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER))
                .thenReturn(false);
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.MANAGER))
                .thenReturn(false);
    }

    // ─── object builders ──────────────────────────────────────────────────────

    private EventBudget budget(String total) {
        return EventBudget.builder()
                .id(UUID.randomUUID())
                .event(event)
                .totalBudget(new BigDecimal(total))
                .notes("Test budget")
                .createdBy(organizer)
                .build();
    }

    private BudgetLineItem lineItem(String planned, String actual, LineItemStatus status) {
        BudgetLineItem item = BudgetLineItem.builder()
                .id(UUID.randomUUID())
                .category(BudgetCategory.VENUE)
                .description("Test expense")
                .plannedAmount(new BigDecimal(planned))
                .actualAmount(actual != null ? new BigDecimal(actual) : null)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
        if (status == LineItemStatus.PAID) {
            item.setPaidAt(LocalDateTime.now());
        }
        return item;
    }

    private CreateBudgetRequest budgetRequest(String amount) {
        CreateBudgetRequest req = new CreateBudgetRequest();
        req.setTotalBudget(new BigDecimal(amount));
        req.setNotes("Unit test budget");
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
