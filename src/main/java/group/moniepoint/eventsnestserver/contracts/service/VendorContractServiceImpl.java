package group.moniepoint.eventsnestserver.contracts.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.budget.model.BudgetCategory;
import group.moniepoint.eventsnestserver.budget.model.BudgetLineItem;
import group.moniepoint.eventsnestserver.budget.model.EventBudget;
import group.moniepoint.eventsnestserver.budget.model.LineItemStatus;
import group.moniepoint.eventsnestserver.budget.repository.BudgetLineItemRepository;
import group.moniepoint.eventsnestserver.budget.repository.EventBudgetRepository;
import group.moniepoint.eventsnestserver.contracts.dto.request.CreateContractRequest;
import group.moniepoint.eventsnestserver.contracts.dto.request.UpdateContractRequest;
import group.moniepoint.eventsnestserver.contracts.dto.response.VendorContractResponse;
import group.moniepoint.eventsnestserver.contracts.model.ContractStatus;
import group.moniepoint.eventsnestserver.contracts.model.VendorContract;
import group.moniepoint.eventsnestserver.contracts.repository.VendorContractRepository;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import group.moniepoint.eventsnestserver.vendor.repository.VendorApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VendorContractServiceImpl implements VendorContractService {

    private final VendorContractRepository contractRepository;
    private final EventRespository eventRepository;
    private final UserRepository userRepository;
    private final VendorApplicationRepository vendorApplicationRepository;
    private final EventMembershipRepository membershipRepository;
    private final EventBudgetRepository budgetRepository;
    private final BudgetLineItemRepository lineItemRepository;

    @Override
    @Transactional
    public VendorContractResponse createContract(UUID eventId, CreateContractRequest request, User caller) {
        Events event = findEventOrThrow(eventId);
        assertOrganizer(eventId, caller);

        User vendor = userRepository.findById(request.getVendorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));

        VendorApplication application = null;
        if (request.getVendorApplicationId() != null) {
            application = vendorApplicationRepository.findById(request.getVendorApplicationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor application not found"));
        }

        VendorContract contract = VendorContract.builder()
                .event(event)
                .organizer(caller)
                .vendor(vendor)
                .vendorApplication(application)
                .title(request.getTitle())
                .description(request.getDescription())
                .terms(request.getTerms())
                .amount(request.getAmount())
                .status(ContractStatus.DRAFT)
                .build();

        contractRepository.save(contract);
        log.info("Contract {} created for event {} by organizer {}", contract.getId(), eventId, caller.getId());
        return VendorContractResponse.from(contract);
    }

    @Override
    @Transactional
    public VendorContractResponse updateContract(UUID contractId, UpdateContractRequest request, User caller) {
        VendorContract contract = findContractOrThrow(contractId);
        assertOrganizer(contract.getEvent().getId(), caller);

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT contracts can be updated");
        }

        if (request.getTitle() != null) contract.setTitle(request.getTitle());
        if (request.getDescription() != null) contract.setDescription(request.getDescription());
        if (request.getTerms() != null) contract.setTerms(request.getTerms());
        if (request.getAmount() != null) contract.setAmount(request.getAmount());

        contractRepository.save(contract);
        return VendorContractResponse.from(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public VendorContractResponse getContract(UUID contractId, User caller) {
        VendorContract contract = findContractOrThrow(contractId);
        assertParticipant(contract, caller);
        return VendorContractResponse.from(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorContractResponse> listByEvent(UUID eventId, User caller) {
        assertOrganizer(eventId, caller);
        return contractRepository.findAllByEventId(eventId).stream()
                .map(VendorContractResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorContractResponse> listMine(User caller) {
        return contractRepository.findAllByVendorId(caller.getId()).stream()
                .map(VendorContractResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public VendorContractResponse signContract(UUID contractId, User caller) {
        VendorContract contract = findContractOrThrow(contractId);

        if (!contract.getVendor().getId().equals(caller.getId())) {
            throw new UnauthorizedException("only the named vendor can sign this contract");
        }
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new IllegalStateException("Contract is not in DRAFT status");
        }

        contract.setStatus(ContractStatus.SIGNED);
        contract.setSignedAt(LocalDateTime.now());
        contractRepository.save(contract);

        // Auto-wire budget: create or add a PLANNED line item for this contract.
        wireBudgetOnSigned(contract);

        log.info("Contract {} signed by vendor {}", contractId, caller.getId());
        return VendorContractResponse.from(contract);
    }

    @Override
    @Transactional
    public VendorContractResponse activateContract(UUID contractId, User caller) {
        VendorContract contract = findContractOrThrow(contractId);
        assertOrganizer(contract.getEvent().getId(), caller);

        if (contract.getStatus() != ContractStatus.FUNDED) {
            throw new IllegalStateException("Contract must be FUNDED before activation");
        }

        contract.setStatus(ContractStatus.ACTIVE);
        contract.setActivatedAt(LocalDateTime.now());
        contractRepository.save(contract);

        log.info("Contract {} activated by organizer {}", contractId, caller.getId());
        return VendorContractResponse.from(contract);
    }

    @Override
    @Transactional
    public VendorContractResponse completeContract(UUID contractId, User caller) {
        VendorContract contract = findContractOrThrow(contractId);
        assertOrganizer(contract.getEvent().getId(), caller);

        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new IllegalStateException("Contract must be ACTIVE to mark as complete");
        }

        contract.setStatus(ContractStatus.COMPLETED);
        contract.setCompletedAt(LocalDateTime.now());
        contractRepository.save(contract);

        log.info("Contract {} completed by organizer {}", contractId, caller.getId());
        return VendorContractResponse.from(contract);
    }

    @Override
    @Transactional
    public VendorContractResponse terminateContract(UUID contractId, User caller) {
        VendorContract contract = findContractOrThrow(contractId);

        boolean isOrganizer = contract.getOrganizer().getId().equals(caller.getId());
        boolean isVendor    = contract.getVendor().getId().equals(caller.getId());
        if (!isOrganizer && !isVendor) {
            throw new UnauthorizedException("only a contract party can terminate it");
        }
        if (contract.getStatus() == ContractStatus.COMPLETED) {
            throw new IllegalStateException("Completed contracts cannot be terminated");
        }

        contract.setStatus(ContractStatus.TERMINATED);
        contract.setTerminatedAt(LocalDateTime.now());
        contractRepository.save(contract);

        log.info("Contract {} terminated by {}", contractId, caller.getId());
        return VendorContractResponse.from(contract);
    }

    // ─── budget wiring ───────────────────────────────────────────────────────────

    private void wireBudgetOnSigned(VendorContract contract) {
        UUID eventId = contract.getEvent().getId();
        budgetRepository.findByEventId(eventId).ifPresent(budget -> {
            BudgetLineItem item = BudgetLineItem.builder()
                    .budget(budget)
                    .category(BudgetCategory.OTHER)
                    .description("Contract: " + contract.getTitle())
                    .plannedAmount(contract.getAmount())
                    .status(LineItemStatus.PLANNED)
                    .createdBy(contract.getOrganizer())
                    .build();
            lineItemRepository.save(item);
            log.debug("Auto-created budget line item for contract {}", contract.getId());
        });
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private VendorContract findContractOrThrow(UUID id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));
    }

    private Events findEventOrThrow(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }

    private void assertOrganizer(UUID eventId, User user) {
        boolean ok = membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER);
        if (!ok) throw new UnauthorizedException("only the event organizer can perform this action");
    }

    private void assertParticipant(VendorContract contract, User user) {
        boolean isOrganizer = contract.getOrganizer().getId().equals(user.getId());
        boolean isVendor    = contract.getVendor().getId().equals(user.getId());
        if (!isOrganizer && !isVendor) {
            throw new UnauthorizedException("access denied");
        }
    }
}
