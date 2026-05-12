package group.moniepoint.eventsnestserver.budget.repository;

import group.moniepoint.eventsnestserver.budget.model.EventBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface EventBudgetRepository extends JpaRepository<EventBudget, UUID> {

    Optional<EventBudget> findByEventId(UUID eventId);

    boolean existsByEventId(UUID eventId);

    /** Sum of actual_amount on PAID line items for a given budget. */
    @Query("SELECT COALESCE(SUM(li.actualAmount), 0) FROM BudgetLineItem li " +
           "WHERE li.budget.id = :budgetId AND li.status = 'PAID'")
    BigDecimal sumActualSpend(@Param("budgetId") UUID budgetId);
}
