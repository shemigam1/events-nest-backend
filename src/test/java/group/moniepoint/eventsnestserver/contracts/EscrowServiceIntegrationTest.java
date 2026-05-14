package group.moniepoint.eventsnestserver.contracts;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.budget.model.EventBudget;
import group.moniepoint.eventsnestserver.budget.model.LineItemStatus;
import group.moniepoint.eventsnestserver.budget.repository.BudgetLineItemRepository;
import group.moniepoint.eventsnestserver.budget.repository.EventBudgetRepository;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.contracts.dto.request.AddMilestoneRequest;
import group.moniepoint.eventsnestserver.contracts.dto.response.EscrowAccountResponse;
import group.moniepoint.eventsnestserver.contracts.dto.response.EscrowMilestoneResponse;
import group.moniepoint.eventsnestserver.contracts.model.*;
import group.moniepoint.eventsnestserver.contracts.repository.EscrowAccountRepository;
import group.moniepoint.eventsnestserver.contracts.repository.VendorContractRepository;
import group.moniepoint.eventsnestserver.contracts.service.EscrowService;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("EscrowService Integration Tests")
@Import(IntegrationTestConfig.class)
class EscrowServiceIntegrationTest {

    @Autowired private EscrowService escrowService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private VendorContractRepository contractRepository;
    @Autowired private EscrowAccountRepository escrowRepository;
    @Autowired private EventBudgetRepository budgetRepository;
    @Autowired private BudgetLineItemRepository lineItemRepository;

    private User organizer;
    private User vendor;
    private Events event;
    private VendorContract signedContract;

    @BeforeEach
    void seed() {
        organizer = userRepository.save(User.builder()
                .id("org001es").firstName("Alice").lastName("Org")
                .email("alice-es@test.com").passwordHash("hashed").role(Role.USER).build());

        vendor = userRepository.save(User.builder()
                .id("ven001es").firstName("Bob").lastName("Vendor")
                .email("bob-es@test.com").passwordHash("hashed").role(Role.USER).build());

        event = eventRepository.save(Events.builder()
                .title("Escrow Test Event").description("Integration test").venue("Test Venue")
                .startTime(LocalDateTime.now().plusDays(30))
                .endTime(LocalDateTime.now().plusDays(30).plusHours(8))
                .status(EventStatus.DRAFT).createdBy(organizer).build());

        membershipRepository.save(EventMembership.builder()
                .events(event).user(organizer).role(EventRole.ORGANIZER).assignedBy(organizer).build());

        signedContract = contractRepository.save(VendorContract.builder()
                .event(event).organizer(organizer).vendor(vendor)
                .title("AV Setup").amount(new BigDecimal("50000.00"))
                .status(ContractStatus.SIGNED).signedAt(LocalDateTime.now()).build());
    }

    // ─── fundEscrow() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fundEscrow()")
    class FundEscrow {

        @Test
        @DisplayName("Organizer funds escrow — escrow created, contract advances to FUNDED")
        void organizerFundsEscrow() {
            EscrowAccountResponse response = escrowService.fundEscrow(signedContract.getId(), organizer);

            assertThat(response.getId()).isNotNull();
            assertThat(response.getStatus()).isEqualTo(EscrowStatus.FUNDED);
            assertThat(response.getTotalAmount()).isEqualByComparingTo("50000.00");
            assertThat(response.getReleasedAmount()).isEqualByComparingTo("0");

            VendorContract updated = contractRepository.findById(signedContract.getId()).get();
            assertThat(updated.getStatus()).isEqualTo(ContractStatus.FUNDED);
            assertThat(updated.getFundedAt()).isNotNull();
        }

        @Test
        @DisplayName("Throws when contract is not SIGNED")
        void throwsWhenNotSigned() {
            signedContract.setStatus(ContractStatus.DRAFT);
            contractRepository.save(signedContract);

            assertThatThrownBy(() -> escrowService.fundEscrow(signedContract.getId(), organizer))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Throws when escrow already exists")
        void throwsWhenAlreadyFunded() {
            escrowService.fundEscrow(signedContract.getId(), organizer);

            // Re-sign so status check passes
            signedContract.setStatus(ContractStatus.SIGNED);
            contractRepository.save(signedContract);

            assertThatThrownBy(() -> escrowService.fundEscrow(signedContract.getId(), organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already funded");
        }

        @Test
        @DisplayName("Throws UnauthorizedException when vendor tries to fund")
        void throwsForVendor() {
            assertThatThrownBy(() -> escrowService.fundEscrow(signedContract.getId(), vendor))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── addMilestone() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("addMilestone()")
    class AddMilestone {

        @Test
        @DisplayName("Organizer adds a milestone — persisted with PENDING status")
        void organizerAddsMilestone() {
            escrowService.fundEscrow(signedContract.getId(), organizer);

            EscrowMilestoneResponse milestone = escrowService.addMilestone(
                    signedContract.getId(), milestoneRequest("Phase 1 Setup", "20000"), organizer);

            assertThat(milestone.getId()).isNotNull();
            assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.PENDING);
            assertThat(milestone.getAmount()).isEqualByComparingTo("20000");
        }

        @Test
        @DisplayName("Throws when no escrow exists for contract")
        void throwsWhenNoEscrow() {
            assertThatThrownBy(() -> escrowService.addMilestone(
                    signedContract.getId(), milestoneRequest("Phase 1", "10000"), organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── full milestone lifecycle ──────────────────────────────────────────────

    @Nested
    @DisplayName("Milestone lifecycle: PENDING → APPROVED → RELEASED")
    class MilestoneLifecycle {

        @Test
        @DisplayName("Full lifecycle: fund, add milestone, approve, release — budget wired")
        void fullMilestoneLifecycle() {
            budgetRepository.save(EventBudget.builder()
                    .event(event).totalBudget(new BigDecimal("500000"))
                    .notes("test").createdBy(organizer).build());

            escrowService.fundEscrow(signedContract.getId(), organizer);
            EscrowMilestoneResponse pending = escrowService.addMilestone(
                    signedContract.getId(), milestoneRequest("Phase 1", "20000"), organizer);

            EscrowMilestoneResponse approved = escrowService.approveMilestone(
                    signedContract.getId(), pending.getId(), organizer);
            assertThat(approved.getStatus()).isEqualTo(MilestoneStatus.APPROVED);

            EscrowMilestoneResponse released = escrowService.releaseMilestone(
                    signedContract.getId(), pending.getId(), organizer);
            assertThat(released.getStatus()).isEqualTo(MilestoneStatus.RELEASED);
            assertThat(released.getReleasedAt()).isNotNull();

            // Escrow status updated
            EscrowAccountResponse escrow = escrowService.getEscrow(signedContract.getId(), organizer);
            assertThat(escrow.getReleasedAmount()).isEqualByComparingTo("20000");

            // Budget line item created (PAID)
            var budgetId = budgetRepository.findByEventId(event.getId()).get().getId();
            List<group.moniepoint.eventsnestserver.budget.model.BudgetLineItem> items =
                    lineItemRepository.findAllByBudgetIdOrderByCreatedAtDesc(budgetId);
            assertThat(items).hasSize(1);
            assertThat(items.get(0).getStatus()).isEqualTo(LineItemStatus.PAID);
            assertThat(items.get(0).getActualAmount()).isEqualByComparingTo("20000");
        }

        @Test
        @DisplayName("Throws when trying to release a PENDING (not yet APPROVED) milestone")
        void throwsReleasingPendingMilestone() {
            escrowService.fundEscrow(signedContract.getId(), organizer);
            EscrowMilestoneResponse pending = escrowService.addMilestone(
                    signedContract.getId(), milestoneRequest("Phase 1", "20000"), organizer);

            assertThatThrownBy(() -> escrowService.releaseMilestone(signedContract.getId(), pending.getId(), organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APPROVED");
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private AddMilestoneRequest milestoneRequest(String title, String amount) {
        AddMilestoneRequest req = new AddMilestoneRequest();
        req.setTitle(title);
        req.setAmount(new BigDecimal(amount));
        req.setDisplayOrder(1);
        return req;
    }
}
