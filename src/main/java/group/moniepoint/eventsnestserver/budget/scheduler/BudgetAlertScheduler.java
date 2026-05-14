package group.moniepoint.eventsnestserver.budget.scheduler;

import group.moniepoint.eventsnestserver.budget.model.EventBudget;
import group.moniepoint.eventsnestserver.budget.repository.EventBudgetRepository;
import group.moniepoint.eventsnestserver.notifications.model.NotificationType;
import group.moniepoint.eventsnestserver.notifications.service.NotificationServiceImpl;
import group.moniepoint.eventsnestserver.sse.dispatcher.SseDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Daily 09:00 check — scans every event budget and fires alerts
 * for any that have crossed the 80% threshold but haven't yet been alerted
 * (idempotency handled by the notification dedupeKey).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertScheduler {

    private static final int ALERT_THRESHOLD_PERCENT = 80;

    private final EventBudgetRepository budgetRepository;
    private final NotificationServiceImpl notificationService;
    private final SseDispatcher sseDispatcher;

    @Scheduled(cron = "0 0 9 * * *")
    public void checkAllBudgets() {
        List<EventBudget> budgets = budgetRepository.findAll();
        log.info("BudgetAlertScheduler: checking {} budgets", budgets.size());

        for (EventBudget budget : budgets) {
            try {
                checkBudget(budget);
            } catch (Exception e) {
                log.error("BudgetAlertScheduler: error checking budget {}: {}", budget.getId(), e.getMessage(), e);
            }
        }
    }

    private void checkBudget(EventBudget budget) {
        BigDecimal totalBudget = budget.getTotalBudget();
        if (totalBudget.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal totalActual = budgetRepository.sumActualSpend(budget.getId());

        int percent = totalActual
                .multiply(BigDecimal.valueOf(100))
                .divide(totalBudget, 0, RoundingMode.FLOOR)
                .intValue();

        if (percent < ALERT_THRESHOLD_PERCENT) return;

        var event = budget.getEvent();
        var organiser = event.getCreatedBy();
        if (organiser == null) return;

        String dedupeKey = "budget:" + budget.getId();

        boolean inserted = notificationService.createIfAbsent(
                organiser.getId(),
                NotificationType.BUDGET_THRESHOLD_REACHED,
                dedupeKey,
                "Budget threshold reached",
                String.format("You have spent %d%% of your budget for \"%s\".", percent, event.getTitle()));

        if (!inserted) return; // already notified

        BigDecimal remaining = totalBudget.subtract(totalActual);
        sseDispatcher.onBudgetThresholdReached(organiser.getId(), Map.of(
                "eventId",          event.getId().toString(),
                "eventTitle",       event.getTitle(),
                "spendPercent",     percent,
                "totalBudget",      totalBudget,
                "totalActualSpend", totalActual,
                "remainingBudget",  remaining));

        log.info("BudgetAlertScheduler: threshold alert sent for event {} — {}% spent",
                event.getId(), percent);
    }
}
