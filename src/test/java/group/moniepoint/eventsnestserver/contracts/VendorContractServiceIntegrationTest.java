package group.moniepoint.eventsnestserver.contracts;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.budget.model.EventBudget;
import group.moniepoint.eventsnestserver.budget.model.LineItemStatus;
import group.moniepoint.eventsnestserver.budget.repository.BudgetLineItemRepository;
import group.moniepoint.eventsnestserver.budget.repository.EventBudgetRepository;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.contracts.dto.request.CreateContractRequest;
import group.moniepoint.eventsnestserver.contracts.dto.request.UpdateContractRequest;
import group.moniepoint.eventsnestserver.contracts.dto.response.VendorContractResponse;
import group.moniepoint.eventsnestserver.contracts.model.ContractStatus;
import group.moniepoint.eventsnestserver.contracts.repository.VendorContractRepository;
import group.moniepoint.eventsnestserver.contracts.service.VendorContractService;
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
@DisplayName("VendorContractService Integration Tests")
@Import(IntegrationTestConfig.class)
class VendorContractServiceIntegrationTest {

    @Autowired private VendorContractService contractService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private VendorContractRepository contractRepository;
    @Autowired private EventBudgetRepository budgetRepository;
    @Autowired private BudgetLineItemRepository lineItemRepository;

    private User organizer;
    private User vendor;
    private User stranger;
    private Events event;

    @BeforeEach
    void seed() {
        organizer = userRepository.save(User.builder()
                .id("org001vc").firstName("Alice").lastName("Org")
                .email("alice-vc@test.com").passwordHash("hashed").role(Role.USER).build());

        vendor = userRepository.save(User.builder()
                .id("ven001vc").firstName("Bob").lastName("Vendor")
                .email("bob-vc@test.com").passwordHash("hashed").role(Role.USER).build());

        stranger = userRepository.save(User.builder()
                .id("str001vc").firstName("Chuck").lastName("Stranger")
                .email("chuck-vc@test.com").passwordHash("hashed").role(Role.USER).build());

        event = eventRepository.save(Events.builder()
                .title("Contract Test Event")
                .description("Integration test")
                .venue("Test Venue")
                .startTime(LocalDateTime.now().plusDays(30))
                .endTime(LocalDateTime.now().plusDays(30).plusHours(8))
                .status(EventStatus.DRAFT)
                .createdBy(organizer)
                .build());

        membershipRepository.save(EventMembership.builder()
                .events(event).user(organizer).role(EventRole.ORGANIZER).assignedBy(organizer).build());
    }

    // ─── createContract() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("createContract()")
    class CreateContract {

        @Test
        @DisplayName("Organizer creates a contract — persisted with DRAFT status")
        void organizerCreatesContract() {
            VendorContractResponse response = contractService.createContract(event.getId(), createRequest(), organizer);

            assertThat(response.getId()).isNotNull();
            assertThat(response.getStatus()).isEqualTo(ContractStatus.DRAFT);
            assertThat(response.getVendorId()).isEqualTo(vendor.getId());
            assertThat(contractRepository.existsById(response.getId())).isTrue();
        }

        @Test
        @DisplayName("Throws UnauthorizedException when stranger creates contract")
        void throwsForStranger() {
            assertThatThrownBy(() -> contractService.createContract(event.getId(), createRequest(), stranger))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when vendor user does not exist")
        void throwsWhenVendorNotFound() {
            CreateContractRequest req = createRequest();
            req.setVendorId("nonexistent-vendor");

            assertThatThrownBy(() -> contractService.createContract(event.getId(), req, organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── updateContract() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateContract()")
    class UpdateContract {

        @Test
        @DisplayName("Organizer updates a DRAFT contract title and amount")
        void organizerUpdatesDraft() {
            VendorContractResponse created = contractService.createContract(event.getId(), createRequest(), organizer);

            UpdateContractRequest req = new UpdateContractRequest();
            req.setTitle("Updated Title");
            req.setAmount(new BigDecimal("99000.00"));

            VendorContractResponse updated = contractService.updateContract(created.getId(), req, organizer);

            assertThat(updated.getTitle()).isEqualTo("Updated Title");
            assertThat(updated.getAmount()).isEqualByComparingTo("99000.00");
        }

        @Test
        @DisplayName("Throws when updating a SIGNED contract")
        void throwsWhenSignedContractUpdated() {
            VendorContractResponse created = contractService.createContract(event.getId(), createRequest(), organizer);
            contractService.signContract(created.getId(), vendor);

            assertThatThrownBy(() -> contractService.updateContract(created.getId(), new UpdateContractRequest(), organizer))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─── signContract() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("signContract()")
    class SignContract {

        @Test
        @DisplayName("Vendor signs contract — status advances to SIGNED and signedAt is set")
        void vendorSignsContract() {
            VendorContractResponse created = contractService.createContract(event.getId(), createRequest(), organizer);

            VendorContractResponse signed = contractService.signContract(created.getId(), vendor);

            assertThat(signed.getStatus()).isEqualTo(ContractStatus.SIGNED);
            assertThat(signed.getSignedAt()).isNotNull();
        }

        @Test
        @DisplayName("Auto-creates a PLANNED budget line item when event has a budget")
        void wiresBudgetOnSign() {
            budgetRepository.save(EventBudget.builder()
                    .event(event).totalBudget(new BigDecimal("1000000"))
                    .notes("test budget").createdBy(organizer).build());

            VendorContractResponse created = contractService.createContract(event.getId(), createRequest(), organizer);
            contractService.signContract(created.getId(), vendor);

            List<group.moniepoint.eventsnestserver.budget.model.BudgetLineItem> items =
                    lineItemRepository.findAllByBudgetIdOrderByCreatedAtDesc(
                            budgetRepository.findByEventId(event.getId()).get().getId());

            assertThat(items).hasSize(1);
            assertThat(items.get(0).getStatus()).isEqualTo(LineItemStatus.PLANNED);
            assertThat(items.get(0).getPlannedAmount()).isEqualByComparingTo("30000.00");
        }

        @Test
        @DisplayName("Throws UnauthorizedException when organizer tries to sign")
        void throwsWhenOrganizerTriesToSign() {
            VendorContractResponse created = contractService.createContract(event.getId(), createRequest(), organizer);

            assertThatThrownBy(() -> contractService.signContract(created.getId(), organizer))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── listByEvent() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listByEvent()")
    class ListByEvent {

        @Test
        @DisplayName("Organizer sees all contracts for their event")
        void organizerSeesAllContracts() {
            contractService.createContract(event.getId(), createRequest(), organizer);
            contractService.createContract(event.getId(), createRequest(), organizer);

            List<VendorContractResponse> list = contractService.listByEvent(event.getId(), organizer);

            assertThat(list).hasSize(2);
        }

        @Test
        @DisplayName("Throws UnauthorizedException when stranger lists contracts")
        void throwsForStranger() {
            assertThatThrownBy(() -> contractService.listByEvent(event.getId(), stranger))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── terminateContract() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("terminateContract()")
    class TerminateContract {

        @Test
        @DisplayName("Either party can terminate — status becomes TERMINATED")
        void organizerCanTerminate() {
            VendorContractResponse created = contractService.createContract(event.getId(), createRequest(), organizer);

            VendorContractResponse terminated = contractService.terminateContract(created.getId(), organizer);

            assertThat(terminated.getStatus()).isEqualTo(ContractStatus.TERMINATED);
            assertThat(terminated.getTerminatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Throws when contract is already COMPLETED")
        void throwsWhenCompleted() {
            VendorContractResponse created = contractService.createContract(event.getId(), createRequest(), organizer);
            var repo = contractRepository.findById(created.getId()).get();
            repo.setStatus(ContractStatus.COMPLETED);
            contractRepository.save(repo);

            assertThatThrownBy(() -> contractService.terminateContract(created.getId(), organizer))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private CreateContractRequest createRequest() {
        CreateContractRequest req = new CreateContractRequest();
        req.setTitle("Sound & Lighting");
        req.setAmount(new BigDecimal("30000.00"));
        req.setVendorId(vendor.getId());
        return req;
    }
}
