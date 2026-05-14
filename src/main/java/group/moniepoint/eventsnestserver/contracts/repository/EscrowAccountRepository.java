package group.moniepoint.eventsnestserver.contracts.repository;

import group.moniepoint.eventsnestserver.contracts.model.EscrowAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EscrowAccountRepository extends JpaRepository<EscrowAccount, UUID> {

    Optional<EscrowAccount> findByContractId(UUID contractId);

    boolean existsByContractId(UUID contractId);
}
