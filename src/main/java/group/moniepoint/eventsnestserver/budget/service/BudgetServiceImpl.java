package group.moniepoint.eventsnestserver.budget.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.bookings.repository.BookingRepository;
import group.moniepoint.eventsnestserver.budget.dto.request.AddLineItemRequest;
import group.moniepoint.eventsnestserver.budget.dto.request.CreateBudgetRequest;
import group.moniepoint.eventsnestserver.budget.dto.request.MarkLineItemPaidRequest;
import group.moniepoint.eventsnestserver.budget.dto.response.BudgetSummaryResponse;
import group.moniepoint.eventsnestserver.budget.dto.response.LineItemResponse;
import group.moniepoint.eventsnestserver.budget.model.BudgetLineItem;
import group.moniepoint.eventsnestserver.budget.model.EventBudget;
import group.moniepoint.eventsnestserver.budget.model.LineItemStatus;
import group.moniepoint.eventsnestserver.budget.repository.BudgetLineItemRepository;
import group.moniepoint.eventsnestserver.budget.repository.EventBudgetRepository;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.email.model.EmailJob;
import group.moniepoint.eventsnestserver.email.model.EmailJobType;
import group.moniepoint.eventsnestserver.email.payload.BudgetAlertPayload;
import group.moniepoint.eventsnestserver.email.repository.EmailJobRepository;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.notifications.model.NotificationType;
import group.moniepoint.eventsnestserver.notifications.service.NotificationServiceImpl;
import group.moniepoint.eventsnestserver.sse.dispatcher.SseDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetServiceImpl implements BudgetService {

    private static final int ALERT_THRESHOLD_PERCENT = 80;

    private final EventBudgetRepository budgetRepository;
    private final BudgetLineItemRepository lineItemRepository;
    private final EventRespository eventRepository;
    private final EventMembershipRepository membershipRepository;
    private final BookingRepository bookingRepository;
    private final NotificationServiceImpl notificationService;
    private final SseDispatcher sseDispatcher;
    private final EmailJobRepository emailJobRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public EventsNestResponse<BudgetSummaryResponse> createBudget(UUID eventId,
                                                                   CreateBudgetRequest request,
                                                                   User caller) {
        Events event = findEventOrThrow(eventId);
        assertOrganizerOrManager(eventId, caller);

        if (budgetRepository.existsByEventId(eventId)) {
            throw new IllegalStateException("A budget already exists for this event. Use update instead.");
        }

        EventBudget budget = EventBudget.builder()
                .event(event)
                .totalBudget(request.getTotalBudget())
                .notes(request.getNotes())
                .createdBy(caller)
                .build();

        budgetRepository.save(budget);

        EventsNestResponse<BudgetSummaryResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Budget created");
        response.setData(buildSummary(budget, eventId));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<BudgetSummaryResponse> updateBudget(UUID eventId,
                                                                   CreateBudgetRequest request,
                                                                   User caller) {
        assertOrganizerOrManager(eventId, caller);
        EventBudget budget = findBudgetOrThrow(eventId);
        budget.setTotalBudget(request.getTotalBudget());
        budget.setNotes(request.getNotes());
        budgetRepository.save(budget);

        EventsNestResponse<BudgetSummaryResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Budget updated");
        response.setData(buildSummary(budget, eventId));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetSummaryResponse getSummary(UUID eventId, User caller) {
        assertOrganizerOrManager(eventId, caller);
        EventBudget budget = findBudgetOrThrow(eventId);
        return buildSummary(budget, eventId);
    }

    @Override
    @Transactional
    public EventsNestResponse<LineItemResponse> addLineItem(UUID eventId,
                                                            AddLineItemRequest request,
                                                            User caller) {
        assertOrganizerOrManager(eventId, caller);
        EventBudget budget = findBudgetOrThrow(eventId);

        BudgetLineItem item = BudgetLineItem.builder()
                .budget(budget)
                .category(request.getCategory())
                .description(request.getDescription())
                .plannedAmount(request.getPlannedAmount())
                .status(LineItemStatus.PLANNED)
                .createdBy(caller)
                .build();

        lineItemRepository.save(item);

        EventsNestResponse<LineItemResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Expense added");
        response.setData(LineItemResponse.from(item));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<LineItemResponse> markPaid(UUID eventId,
                                                          UUID itemId,
                                                          MarkLineItemPaidRequest request,
                                                          User caller) {
        assertOrganizerOrManager(eventId, caller);
        EventBudget budget = findBudgetOrThrow(eventId);

        BudgetLineItem item = lineItemRepository.findByIdAndBudgetId(itemId, budget.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Line item not found"));

        item.setActualAmount(request.getActualAmount());
        item.setStatus(LineItemStatus.PAID);
        item.setPaidAt(LocalDateTime.now());
        lineItemRepository.save(item);

        // Re-compute actual spend and check the 80% threshold.
        checkAndFireBudgetAlert(budget, eventId);

        EventsNestResponse<LineItemResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Expense marked as paid");
        response.setData(LineItemResponse.from(item));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<Void> deleteLineItem(UUID eventId, UUID itemId, User caller) {
        assertOrganizerOrManager(eventId, caller);
        EventBudget budget = findBudgetOrThrow(eventId);

        BudgetLineItem item = lineItemRepository.findByIdAndBudgetId(itemId, budget.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Line item not found"));

        if (item.getStatus() == LineItemStatus.PAID) {
            throw new IllegalStateException("Paid expenses cannot be deleted");
        }

        lineItemRepository.delete(item);

        EventsNestResponse<Void> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Line item deleted");
        return response;
    }

    // ─── alert logic ─────────────────────────────────────────────────────────────

    /**
     * Called after every markPaid. Computes total actual spend, checks if it
     * has crossed the 80% ceiling for the first time, and — if so — fires:
     *   1. A deduplicated DB notification (idempotent via type+dedupeKey).
     *   2. An SSE push to the organiser if they are online.
     *   3. An email job (picked up by EmailJobPoller).
     *
     * The dedupe key is "budget:{budgetId}" — so this fires exactly once per
     * budget regardless of how many subsequent expenses are marked paid.
     */
    private void checkAndFireBudgetAlert(EventBudget budget, UUID eventId) {
        BigDecimal totalActual = budgetRepository.sumActualSpend(budget.getId());
        BigDecimal totalBudget = budget.getTotalBudget();

        if (totalBudget.compareTo(BigDecimal.ZERO) == 0) return;

        int percent = totalActual
                .multiply(BigDecimal.valueOf(100))
                .divide(totalBudget, 0, RoundingMode.FLOOR)
                .intValue();

        if (percent < ALERT_THRESHOLD_PERCENT) return;

        String dedupeKey = "budget:" + budget.getId();
        Events event = budget.getEvent();
        User organiser = event.getCreatedBy();
        if (organiser == null) return;

        // 1. Notification row (idempotent)
        boolean inserted = notificationService.createIfAbsent(
                organiser.getId(),
                NotificationType.BUDGET_THRESHOLD_REACHED,
                dedupeKey,
                "Budget threshold reached",
                String.format("You have spent %d%% of your budget for \"%s\".", percent, event.getTitle()));

        if (!inserted) return; // already fired — nothing more to do

        BigDecimal remaining = totalBudget.subtract(totalActual);

        // 2. SSE push
        sseDispatcher.onBudgetThresholdReached(organiser.getId(), Map.of(
                "eventId",         eventId.toString(),
                "eventTitle",      event.getTitle(),
                "spendPercent",    percent,
                "totalBudget",     totalBudget,
                "totalActualSpend",totalActual,
                "remainingBudget", remaining));

        // 3. Email (enqueued — non-blocking, retried by poller)
        if (organiser.getEmail() != null && !organiser.getEmail().isBlank()) {
            String organiserName = trim(organiser.getFirstName()) + " " + trim(organiser.getLastName());
            BudgetAlertPayload payload = new BudgetAlertPayload(
                    organiserName.isBlank() ? "there" : organiserName.trim(),
                    event.getTitle(),
                    eventId,
                    totalBudget,
                    totalActual,
                    remaining,
                    percent);
            try {
                emailJobRepository.save(EmailJob.builder()
                        .type(EmailJobType.BUDGET_ALERT)
                        .toEmail(organiser.getEmail())
                        .payloadJson(objectMapper.writeValueAsString(payload))
                        .build());
            } catch (JsonProcessingException e) {
                log.error("Failed to serialise BUDGET_ALERT payload for event {}: {}", eventId, e.getMessage());
            }
        }

        log.info("Budget threshold alert fired for event {} — {}% spent", eventId, percent);
    }

    // ─── summary builder ─────────────────────────────────────────────────────────

    private BudgetSummaryResponse buildSummary(EventBudget budget, UUID eventId) {
        List<BudgetLineItem> items = lineItemRepository.findAllByBudgetIdOrderByCreatedAtDesc(budget.getId());

        BigDecimal totalPlanned = items.stream()
                .map(BudgetLineItem::getPlannedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalActual = items.stream()
                .filter(i -> i.getStatus() == LineItemStatus.PAID && i.getActualAmount() != null)
                .map(BudgetLineItem::getActualAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBudget = budget.getTotalBudget();
        BigDecimal remaining   = totalBudget.subtract(totalActual);

        double spendPercent = totalBudget.compareTo(BigDecimal.ZERO) == 0 ? 0
                : totalActual.multiply(BigDecimal.valueOf(100))
                        .divide(totalBudget, 2, RoundingMode.HALF_UP)
                        .doubleValue();

        BigDecimal revenue  = bookingRepository.sumConfirmedRevenueByEventId(eventId);
        BigDecimal netProfit = revenue.subtract(totalActual);

        List<LineItemResponse> lineItemResponses = items.stream()
                .map(LineItemResponse::from)
                .toList();

        return BudgetSummaryResponse.builder()
                .budgetId(budget.getId())
                .eventId(eventId)
                .totalBudget(totalBudget)
                .totalPlanned(totalPlanned)
                .totalActualSpend(totalActual)
                .remainingBudget(remaining)
                .spendPercent(spendPercent)
                .thresholdAlertTriggered(spendPercent >= ALERT_THRESHOLD_PERCENT)
                .totalRevenue(revenue)
                .netProfit(netProfit)
                .notes(budget.getNotes())
                .lineItems(lineItemResponses)
                .build();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private void assertOrganizerOrManager(UUID eventId, User user) {
        boolean isOrganizer = membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER);
        boolean isManager   = membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.MANAGER);
        if (!isOrganizer && !isManager) {
            throw new UnauthorizedException("only the event organiser or manager can access the budget");
        }
    }

    private Events findEventOrThrow(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }

    private EventBudget findBudgetOrThrow(UUID eventId) {
        return budgetRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No budget found for this event"));
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
