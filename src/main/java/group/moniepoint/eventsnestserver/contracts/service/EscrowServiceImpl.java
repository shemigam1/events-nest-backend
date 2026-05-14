package group.moniepoint.eventsnestserver.contracts.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.budget.model.BudgetCategory;
import group.moniepoint.eventsnestserver.budget.model.BudgetLineItem;
import group.moniepoint.eventsnestserver.budget.model.LineItemStatus;
import group.moniepoint.eventsnestserver.budget.repository.BudgetLineItemRepository;
import group.moniepoint.eventsnestserver.budget.repository.EventBudgetRepository;
import group.moniepoint.eventsnestserver.contracts.dto.request.AddMilestoneRequest;
import group.moniepoint.eventsnestserver.contracts.dto.response.EscrowAccountResponse;
import group.moniepoint.eventsnestserver.contracts.dto.response.EscrowMilestoneResponse;
import group.moniepoint.eventsnestserver.contracts.model.*;
import group.moniepoint.eventsnestserver.contracts.repository.EscrowAccountRepository;
import group.moniepoint.eventsnestserver.contracts.repository.EscrowMilestoneRepository;
import group.moniepoint.eventsnestserver.contracts.repository.VendorContractRepository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EscrowServiceImpl implements EscrowService {

    private final VendorContractRepository contractRepository;
    private final EscrowAccountRepository escrowRepository;
    private final EscrowMilestoneRepository milestoneRepository;
    private final EventBudgetRepository budgetRepository;
    private final BudgetLineItemRepository lineItemRepository;

    @Override
    @Transactional
    public EscrowAccountResponse fundEscrow(UUID contractId, User caller) {
        VendorContract contract = findContractOrThrow(contractId);
        assertOrganizer(contract, caller);

        if (contract.getStatus() != ContractStatus.SIGNED) {
            throw new IllegalStateException("Contract must be SIGNED before funding escrow");
        }
        if (escrowRepository.existsByContractId(contractId)) {
            throw new IllegalStateException("Escrow already funded for this contract");
        }

        EscrowAccount escrow = EscrowAccount.builder()
                .contract(contract)
                .totalAmount(contract.getAmount())
                .releasedAmount(BigDecimal.ZERO)
                .status(EscrowStatus.FUNDED)
                .fundedAt(LocalDateTime.now())
                .build();
        escrowRepository.save(escrow);

        contract.setStatus(ContractStatus.FUNDED);
        contract.setFundedAt(LocalDateTime.now());
        contractRepository.save(contract);

        log.info("Escrow funded for contract {} by organizer {}", contractId, caller.getId());
        return EscrowAccountResponse.from(escrow);
    }

    @Override
    @Transactional(readOnly = true)
    public EscrowAccountResponse getEscrow(UUID contractId, User caller) {
        VendorContract contract = findContractOrThrow(contractId);
        assertParticipant(contract, caller);
        EscrowAccount escrow = findEscrowOrThrow(contractId);
        return EscrowAccountResponse.from(escrow);
    }

    @Override
    @Transactional
    public EscrowMilestoneResponse addMilestone(UUID contractId, AddMilestoneRequest request, User caller) {
        VendorContract contract = findContractOrThrow(contractId);
        assertOrganizer(contract, caller);

        EscrowAccount escrow = findEscrowOrThrow(contractId);

        EscrowMilestone milestone = EscrowMilestone.builder()
                .escrow(escrow)
                .title(request.getTitle())
                .description(request.getDescription())
                .amount(request.getAmount())
                .displayOrder(request.getDisplayOrder())
                .status(MilestoneStatus.PENDING)
                .build();
        milestoneRepository.save(milestone);

        log.debug("Milestone {} added to escrow {} for contract {}", milestone.getId(), escrow.getId(), contractId);
        return EscrowMilestoneResponse.from(milestone);
    }

    @Override
    @Transactional
    public EscrowMilestoneResponse approveMilestone(UUID contractId, UUID milestoneId, User caller) {
        VendorContract contract = findContractOrThrow(contractId);
        assertOrganizer(contract, caller);

        EscrowAccount escrow = findEscrowOrThrow(contractId);
        EscrowMilestone milestone = findMilestoneOrThrow(milestoneId, escrow.getId());

        if (milestone.getStatus() != MilestoneStatus.PENDING) {
            throw new IllegalStateException("Only PENDING milestones can be approved");
        }

        milestone.setStatus(MilestoneStatus.APPROVED);
        milestone.setApprovedAt(LocalDateTime.now());
        milestoneRepository.save(milestone);

        return EscrowMilestoneResponse.from(milestone);
    }

    @Override
    @Transactional
    public EscrowMilestoneResponse releaseMilestone(UUID contractId, UUID milestoneId, User caller) {
        VendorContract contract = findContractOrThrow(contractId);
        assertOrganizer(contract, caller);

        EscrowAccount escrow = findEscrowOrThrow(contractId);
        EscrowMilestone milestone = findMilestoneOrThrow(milestoneId, escrow.getId());

        if (milestone.getStatus() != MilestoneStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED milestones can have funds released");
        }

        milestone.setStatus(MilestoneStatus.RELEASED);
        milestone.setReleasedAt(LocalDateTime.now());
        milestoneRepository.save(milestone);

        // Update escrow released total and status
        BigDecimal released = milestoneRepository.sumReleasedByEscrowId(escrow.getId());
        escrow.setReleasedAmount(released);
        escrow.setStatus(released.compareTo(escrow.getTotalAmount()) >= 0
                ? EscrowStatus.FULLY_RELEASED
                : EscrowStatus.PARTIALLY_RELEASED);
        escrowRepository.save(escrow);

        // Wire to budget: mark a PAID line item for the released milestone amount
        wireMilestoneReleaseToBudget(contract, milestone);

        log.info("Milestone {} released for contract {} — {} released so far",
                milestoneId, contractId, released);
        return EscrowMilestoneResponse.from(milestone);
    }

    // ─── budget wiring ───────────────────────────────────────────────────────────

    private void wireMilestoneReleaseToBudget(VendorContract contract, EscrowMilestone milestone) {
        UUID eventId = contract.getEvent().getId();
        budgetRepository.findByEventId(eventId).ifPresent(budget -> {
            BudgetLineItem item = BudgetLineItem.builder()
                    .budget(budget)
                    .category(BudgetCategory.OTHER)
                    .description("Milestone released: " + milestone.getTitle()
                            + " (contract: " + contract.getTitle() + ")")
                    .plannedAmount(milestone.getAmount())
                    .actualAmount(milestone.getAmount())
                    .status(LineItemStatus.PAID)
                    .paidAt(LocalDateTime.now())
                    .createdBy(contract.getOrganizer())
                    .build();
            lineItemRepository.save(item);
        });
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private VendorContract findContractOrThrow(UUID id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));
    }

    private EscrowAccount findEscrowOrThrow(UUID contractId) {
        return escrowRepository.findByContractId(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Escrow not found for this contract"));
    }

    private EscrowMilestone findMilestoneOrThrow(UUID milestoneId, UUID escrowId) {
        return milestoneRepository.findByIdAndEscrowId(milestoneId, escrowId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found"));
    }

    private void assertOrganizer(VendorContract contract, User user) {
        if (!contract.getOrganizer().getId().equals(user.getId())) {
            throw new UnauthorizedException("only the contract organizer can perform this action");
        }
    }

    private void assertParticipant(VendorContract contract, User user) {
        boolean isOrganizer = contract.getOrganizer().getId().equals(user.getId());
        boolean isVendor    = contract.getVendor().getId().equals(user.getId());
        if (!isOrganizer && !isVendor) {
            throw new UnauthorizedException("access denied");
        }
    }
}
