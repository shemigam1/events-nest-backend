package group.moniepoint.eventsnestserver.budget.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.budget.dto.request.AddLineItemRequest;
import group.moniepoint.eventsnestserver.budget.dto.request.CreateBudgetRequest;
import group.moniepoint.eventsnestserver.budget.dto.request.MarkLineItemPaidRequest;
import group.moniepoint.eventsnestserver.budget.dto.response.BudgetSummaryResponse;
import group.moniepoint.eventsnestserver.budget.dto.response.LineItemResponse;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;

import java.util.UUID;

public interface BudgetService {

    /** Create the budget envelope for an event. One per event — errors if already exists. */
    EventsNestResponse<BudgetSummaryResponse> createBudget(UUID eventId, CreateBudgetRequest request, User caller);

    /** Update the budget ceiling or notes. */
    EventsNestResponse<BudgetSummaryResponse> updateBudget(UUID eventId, CreateBudgetRequest request, User caller);

    /** Full summary: budget, planned, actual spend, revenue, P&L, line items. */
    BudgetSummaryResponse getSummary(UUID eventId, User caller);

    /** Add a new planned expense line item. */
    EventsNestResponse<LineItemResponse> addLineItem(UUID eventId, AddLineItemRequest request, User caller);

    /** Mark a planned line item as paid with its actual amount. Triggers 80% alert if threshold crossed. */
    EventsNestResponse<LineItemResponse> markPaid(UUID eventId, UUID itemId, MarkLineItemPaidRequest request, User caller);

    /** Delete a line item (only PLANNED items can be deleted). */
    EventsNestResponse<Void> deleteLineItem(UUID eventId, UUID itemId, User caller);
}
