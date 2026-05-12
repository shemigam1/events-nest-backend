package group.moniepoint.eventsnestserver.budget.dto.response;

import group.moniepoint.eventsnestserver.budget.model.BudgetCategory;
import group.moniepoint.eventsnestserver.budget.model.BudgetLineItem;
import group.moniepoint.eventsnestserver.budget.model.LineItemStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class LineItemResponse {
    private UUID id;
    private BudgetCategory category;
    private String description;
    private BigDecimal plannedAmount;
    private BigDecimal actualAmount;
    private LineItemStatus status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    public static LineItemResponse from(BudgetLineItem item) {
        return LineItemResponse.builder()
                .id(item.getId())
                .category(item.getCategory())
                .description(item.getDescription())
                .plannedAmount(item.getPlannedAmount())
                .actualAmount(item.getActualAmount())
                .status(item.getStatus())
                .paidAt(item.getPaidAt())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
