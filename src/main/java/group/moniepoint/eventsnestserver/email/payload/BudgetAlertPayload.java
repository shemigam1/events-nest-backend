package group.moniepoint.eventsnestserver.email.payload;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetAlertPayload(
        String organiserName,
        String eventTitle,
        UUID eventId,
        BigDecimal totalBudget,
        BigDecimal totalActualSpend,
        BigDecimal remainingBudget,
        int spendPercent
) {}
