package group.moniepoint.eventsnestserver.contracts;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.budget.model.EventBudget;
import group.moniepoint.eventsnestserver.budget.repository.BudgetLineItemRepository;
import group.moniepoint.eventsnestserver.budget.repository.EventBudgetRepository;
import group.moniepoint.eventsnestserver.contracts.dto.request.AddMilestoneRequest;
import group.moniepoint.eventsnestserver.contracts.dto.response.EscrowAccountResponse;
import group.moniepoint.eventsnestserver.contracts.dto.response.EscrowMilestoneResponse;
import group.moniepoint.eventsnestserver.contracts.model.*;
import group.moniepoint.eventsnestserver.contracts.repository.EscrowAccountRepository;
import group.moniepoint.eventsnestserver.contracts.repository.EscrowMilestoneRepository;
import group.moniepoint.eventsnestserver.contracts.repository.VendorContractRepository;
import group.moniepoint.eventsnestserver.contracts.service.EscrowServiceImpl;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EscrowService Unit Tests")
class EscrowServiceTest {

    @Mock private VendorContractRepository contractRepository;
    @Mock private EscrowAccountRepository escrowRepository;
    @Mock private EscrowMilestoneRepository milestoneRepository;
    @Mock private EventBudgetRepository budgetRepository;
    @Mock private BudgetLineItemRepository lineItemRepository;

    private EscrowServiceImpl service;

    private User organizer;
    private User vendor;
    private Events event;
    private UUID eventId;
    private UUID contractId;
    private UUID escrowId;
    private UUID milestoneId;

    @BeforeEach
    void setUp() {
        service = new EscrowServiceImpl(
                contractRepository, escrowRepository, milestoneRepository,
                budgetRepository, lineItemRepository);

        organizer = User.builder().id("org001").firstName("Alice").lastName("Org")
                .email("alice@test.com").role(Role.USER).build();
        vendor = User.builder().id("ven001").firstName("Bob").lastName("Vendor")
                .email("bob@test.com").role(Role.USER).build();

        eventId    = UUID.randomUUID();
        contractId = UUID.randomUUID();
        escrowId   = UUID.randomUUID();
        milestoneId = UUID.randomUUID();
        event = Events.builder().id(eventId).title("Test Event").createdBy(organizer).build();
    }

    // ─── fundEscrow() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fundEscrow()")
    class FundEscrow {

        @Test
        @DisplayName("Organizer funds a SIGNED contract — creates escrow and advances contract to FUNDED")
        void organizerFundsSignedContract() {
            VendorContract contract = signedContract();
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(escrowRepository.existsByContractId(contractId)).thenReturn(false);
            when(escrowRepository.save(any())).thenAnswer(i -> {
                EscrowAccount ea = i.getArgument(0);
                ea.setId(escrowId);
                return ea;
            });
            when(contractRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            EscrowAccountResponse response = service.fundEscrow(contractId, organizer);

            assertThat(response.getStatus()).isEqualTo(EscrowStatus.FUNDED);
            assertThat(response.getTotalAmount()).isEqualByComparingTo("50000.00");
            assertThat(contract.getStatus()).isEqualTo(ContractStatus.FUNDED);
            assertThat(contract.getFundedAt()).isNotNull();
        }

        @Test
        @DisplayName("Throws when contract is not SIGNED")
        void throwsWhenNotSigned() {
            VendorContract contract = signedContract();
            contract.setStatus(ContractStatus.DRAFT);
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

            assertThatThrownBy(() -> service.fundEscrow(contractId, organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SIGNED");
        }

        @Test
        @DisplayName("Throws when escrow already exists")
        void throwsWhenEscrowAlreadyFunded() {
            VendorContract contract = signedContract();
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(escrowRepository.existsByContractId(contractId)).thenReturn(true);

            assertThatThrownBy(() -> service.fundEscrow(contractId, organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already funded");
        }

        @Test
        @DisplayName("Throws UnauthorizedException when vendor tries to fund escrow")
        void throwsWhenVendorTriesToFund() {
            VendorContract contract = signedContract();
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

            assertThatThrownBy(() -> service.fundEscrow(contractId, vendor))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── addMilestone() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("addMilestone()")
    class AddMilestone {

        @Test
        @DisplayName("Organizer adds a milestone to an escrow account")
        void organizerAddsMilestone() {
            VendorContract contract = fundedContract();
            EscrowAccount escrow = escrowAccount(contract);
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(escrowRepository.findByContractId(contractId)).thenReturn(Optional.of(escrow));
            when(milestoneRepository.save(any())).thenAnswer(i -> {
                EscrowMilestone m = i.getArgument(0);
                m.setId(milestoneId);
                return m;
            });

            AddMilestoneRequest req = milestoneRequest("Phase 1", "20000.00");
            EscrowMilestoneResponse response = service.addMilestone(contractId, req, organizer);

            assertThat(response.getTitle()).isEqualTo("Phase 1");
            assertThat(response.getAmount()).isEqualByComparingTo("20000.00");
            assertThat(response.getStatus()).isEqualTo(MilestoneStatus.PENDING);
        }

        @Test
        @DisplayName("Throws when escrow is not found for contract")
        void throwsWhenNoEscrow() {
            VendorContract contract = fundedContract();
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(escrowRepository.findByContractId(contractId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addMilestone(contractId, milestoneRequest("Phase 1", "10000"), organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── approveMilestone() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("approveMilestone()")
    class ApproveMilestone {

        @Test
        @DisplayName("Organizer approves a PENDING milestone")
        void organizerApprovesMilestone() {
            VendorContract contract = fundedContract();
            EscrowAccount escrow = escrowAccount(contract);
            EscrowMilestone milestone = pendingMilestone(escrow);

            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(escrowRepository.findByContractId(contractId)).thenReturn(Optional.of(escrow));
            when(milestoneRepository.findByIdAndEscrowId(milestoneId, escrowId)).thenReturn(Optional.of(milestone));
            when(milestoneRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            EscrowMilestoneResponse response = service.approveMilestone(contractId, milestoneId, organizer);

            assertThat(response.getStatus()).isEqualTo(MilestoneStatus.APPROVED);
            assertThat(response.getApprovedAt()).isNotNull();
        }

        @Test
        @DisplayName("Throws when milestone is already RELEASED")
        void throwsWhenAlreadyReleased() {
            VendorContract contract = fundedContract();
            EscrowAccount escrow = escrowAccount(contract);
            EscrowMilestone milestone = pendingMilestone(escrow);
            milestone.setStatus(MilestoneStatus.RELEASED);

            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(escrowRepository.findByContractId(contractId)).thenReturn(Optional.of(escrow));
            when(milestoneRepository.findByIdAndEscrowId(milestoneId, escrowId)).thenReturn(Optional.of(milestone));

            assertThatThrownBy(() -> service.approveMilestone(contractId, milestoneId, organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PENDING");
        }
    }

    // ─── releaseMilestone() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("releaseMilestone()")
    class ReleaseMilestone {

        @Test
        @DisplayName("Organizer releases funds for an APPROVED milestone — status becomes RELEASED")
        void organizerReleasesMilestone() {
            VendorContract contract = fundedContract();
            EscrowAccount escrow = escrowAccount(contract);
            EscrowMilestone milestone = pendingMilestone(escrow);
            milestone.setStatus(MilestoneStatus.APPROVED);

            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(escrowRepository.findByContractId(contractId)).thenReturn(Optional.of(escrow));
            when(milestoneRepository.findByIdAndEscrowId(milestoneId, escrowId)).thenReturn(Optional.of(milestone));
            when(milestoneRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(milestoneRepository.sumReleasedByEscrowId(escrowId)).thenReturn(new BigDecimal("20000.00"));
            when(escrowRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.empty());

            EscrowMilestoneResponse response = service.releaseMilestone(contractId, milestoneId, organizer);

            assertThat(response.getStatus()).isEqualTo(MilestoneStatus.RELEASED);
            assertThat(response.getReleasedAt()).isNotNull();
        }

        @Test
        @DisplayName("Escrow becomes FULLY_RELEASED when all funds are released")
        void escrowBecomesFullyReleased() {
            VendorContract contract = fundedContract();
            EscrowAccount escrow = escrowAccount(contract);
            EscrowMilestone milestone = pendingMilestone(escrow);
            milestone.setStatus(MilestoneStatus.APPROVED);

            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(escrowRepository.findByContractId(contractId)).thenReturn(Optional.of(escrow));
            when(milestoneRepository.findByIdAndEscrowId(milestoneId, escrowId)).thenReturn(Optional.of(milestone));
            when(milestoneRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            // Released == total
            when(milestoneRepository.sumReleasedByEscrowId(escrowId)).thenReturn(new BigDecimal("50000.00"));
            when(escrowRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.empty());

            service.releaseMilestone(contractId, milestoneId, organizer);

            ArgumentCaptor<EscrowAccount> captor = ArgumentCaptor.forClass(EscrowAccount.class);
            verify(escrowRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(EscrowStatus.FULLY_RELEASED);
        }

        @Test
        @DisplayName("Creates a PAID budget line item when a budget exists")
        void wiresBudgetOnRelease() {
            VendorContract contract = fundedContract();
            EscrowAccount escrow = escrowAccount(contract);
            EscrowMilestone milestone = pendingMilestone(escrow);
            milestone.setStatus(MilestoneStatus.APPROVED);
            EventBudget budget = EventBudget.builder().id(UUID.randomUUID()).event(event)
                    .totalBudget(new BigDecimal("1000000")).createdBy(organizer).build();

            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(escrowRepository.findByContractId(contractId)).thenReturn(Optional.of(escrow));
            when(milestoneRepository.findByIdAndEscrowId(milestoneId, escrowId)).thenReturn(Optional.of(milestone));
            when(milestoneRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(milestoneRepository.sumReleasedByEscrowId(escrowId)).thenReturn(new BigDecimal("20000.00"));
            when(escrowRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(budget));
            when(lineItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.releaseMilestone(contractId, milestoneId, organizer);

            verify(lineItemRepository).save(any());
        }

        @Test
        @DisplayName("Throws when milestone is not APPROVED")
        void throwsWhenNotApproved() {
            VendorContract contract = fundedContract();
            EscrowAccount escrow = escrowAccount(contract);
            EscrowMilestone milestone = pendingMilestone(escrow);

            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(escrowRepository.findByContractId(contractId)).thenReturn(Optional.of(escrow));
            when(milestoneRepository.findByIdAndEscrowId(milestoneId, escrowId)).thenReturn(Optional.of(milestone));

            assertThatThrownBy(() -> service.releaseMilestone(contractId, milestoneId, organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APPROVED");
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private VendorContract signedContract() {
        return VendorContract.builder().id(contractId).event(event).organizer(organizer).vendor(vendor)
                .title("AV Setup").amount(new BigDecimal("50000.00")).status(ContractStatus.SIGNED).build();
    }

    private VendorContract fundedContract() {
        return VendorContract.builder().id(contractId).event(event).organizer(organizer).vendor(vendor)
                .title("AV Setup").amount(new BigDecimal("50000.00")).status(ContractStatus.FUNDED).build();
    }

    private EscrowAccount escrowAccount(VendorContract contract) {
        return EscrowAccount.builder().id(escrowId).contract(contract)
                .totalAmount(new BigDecimal("50000.00")).releasedAmount(BigDecimal.ZERO)
                .status(EscrowStatus.FUNDED).milestones(new ArrayList<>()).build();
    }

    private EscrowMilestone pendingMilestone(EscrowAccount escrow) {
        return EscrowMilestone.builder().id(milestoneId).escrow(escrow)
                .title("Phase 1").amount(new BigDecimal("20000.00"))
                .status(MilestoneStatus.PENDING).displayOrder(1).build();
    }

    private AddMilestoneRequest milestoneRequest(String title, String amount) {
        AddMilestoneRequest req = new AddMilestoneRequest();
        req.setTitle(title);
        req.setAmount(new BigDecimal(amount));
        req.setDisplayOrder(1);
        return req;
    }
}
