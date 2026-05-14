package group.moniepoint.eventsnestserver.contracts.repository;

import group.moniepoint.eventsnestserver.contracts.model.EscrowMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EscrowMilestoneRepository extends JpaRepository<EscrowMilestone, UUID> {

    List<EscrowMilestone> findAllByEscrowIdOrderByDisplayOrderAsc(UUID escrowId);

    Optional<EscrowMilestone> findByIdAndEscrowId(UUID id, UUID escrowId);

    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM EscrowMilestone m WHERE m.escrow.id = :escrowId AND m.status = 'RELEASED'")
    BigDecimal sumReleasedByEscrowId(@Param("escrowId") UUID escrowId);
}
