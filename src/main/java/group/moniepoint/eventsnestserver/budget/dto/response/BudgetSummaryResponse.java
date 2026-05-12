package group.moniepoint.eventsnestserver.budget.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class BudgetSummaryResponse {

    private UUID budgetId;
    private UUID eventId;

    /** Organiser's declared ceiling. */
    private BigDecimal totalBudget;

    /** Sum of plannedAmount across all line items. */
    private BigDecimal totalPlanned;

    /** Sum of actualAmount on PAID line items only. */
    private BigDecimal totalActualSpend;

    /** Remaining budget = totalBudget - totalActualSpend. */
    private BigDecimal remainingBudget;

    /** Percentage of budget consumed by actual spend (0–100+). */
    private double spendPercent;

    /** Whether the 80% alert threshold has been crossed. */
    private boolean thresholdAlertTriggered;

    /** Total confirmed ticket revenue from bookings (auto-computed). */
    private BigDecimal totalRevenue;

    /** Net P&L = totalRevenue - totalActualSpend. */
    private BigDecimal netProfit;

    private String notes;
    private List<LineItemResponse> lineItems;
}
