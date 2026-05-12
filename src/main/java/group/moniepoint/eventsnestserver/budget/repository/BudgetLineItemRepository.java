package group.moniepoint.eventsnestserver.budget.repository;

import group.moniepoint.eventsnestserver.budget.model.BudgetLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetLineItemRepository extends JpaRepository<BudgetLineItem, UUID> {

    List<BudgetLineItem> findAllByBudgetIdOrderByCreatedAtDesc(UUID budgetId);

    Optional<BudgetLineItem> findByIdAndBudgetId(UUID id, UUID budgetId);
}
