package group.moniepoint.eventsnestserver.contracts;

import group.moniepoint.eventsnestserver.audit.publisher.AuditEventPublisher;
import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.contracts.kafka.ContractEventPublisher;
import group.moniepoint.eventsnestserver.budget.model.BudgetLineItem;
import group.moniepoint.eventsnestserver.budget.model.EventBudget;
import group.moniepoint.eventsnestserver.budget.model.LineItemStatus;
import group.moniepoint.eventsnestserver.budget.repository.BudgetLineItemRepository;
import group.moniepoint.eventsnestserver.budget.repository.EventBudgetRepository;
import group.moniepoint.eventsnestserver.chat.repository.ConversationRepository;
import group.moniepoint.eventsnestserver.chat.repository.MessageRepository;
import group.moniepoint.eventsnestserver.chat.model.Conversation;
import group.moniepoint.eventsnestserver.chat.model.ConversationType;
import group.moniepoint.eventsnestserver.email.EmailOutbox;
import group.moniepoint.eventsnestserver.contracts.dto.request.CreateContractRequest;
import group.moniepoint.eventsnestserver.contracts.dto.request.UpdateContractRequest;
import group.moniepoint.eventsnestserver.contracts.dto.response.VendorContractResponse;
import group.moniepoint.eventsnestserver.contracts.model.ContractStatus;
import group.moniepoint.eventsnestserver.contracts.model.VendorContract;
import group.moniepoint.eventsnestserver.contracts.repository.VendorContractRepository;
import group.moniepoint.eventsnestserver.contracts.service.VendorContractServiceImpl;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.vendor.repository.VendorApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
@DisplayName("VendorContractService Unit Tests")
class VendorContractServiceTest {

    @Mock private VendorContractRepository contractRepository;
    @Mock private EventRespository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private VendorApplicationRepository vendorApplicationRepository;
    @Mock private EventMembershipRepository membershipRepository;
    @Mock private EventBudgetRepository budgetRepository;
    @Mock private BudgetLineItemRepository lineItemRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private EmailOutbox emailOutbox;
    @Mock private ContractEventPublisher contractEventPublisher;
    @Mock private AuditEventPublisher auditEventPublisher;

    private VendorContractServiceImpl service;

    private User organizer;
    private User vendor;
    private User stranger;
    private Events event;
    private UUID eventId;
    private UUID contractId;

    @BeforeEach
    void setUp() {
        service = new VendorContractServiceImpl(
                contractRepository, eventRepository, userRepository,
                vendorApplicationRepository, membershipRepository,
                budgetRepository, lineItemRepository,
                conversationRepository, messageRepository, emailOutbox,
                contractEventPublisher, auditEventPublisher);

        organizer = User.builder().id("org001").firstName("Alice").lastName("Org")
                .email("alice@test.com").role(Role.USER).build();
        vendor = User.builder().id("ven001").firstName("Bob").lastName("Vendor")
                .email("bob@test.com").role(Role.USER).build();
        stranger = User.builder().id("str001").firstName("Chuck").lastName("Stranger")
                .email("chuck@test.com").role(Role.USER).build();

        eventId = UUID.randomUUID();
        contractId = UUID.randomUUID();
        event = Events.builder().id(eventId).title("Test Event").createdBy(organizer).build();
    }

    // ─── createContract() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("createContract()")
    class CreateContract {

        @Test
        @DisplayName("Organizer creates a contract successfully")
        void organizerCreatesContract() {
            stubIsOrganizer(organizer);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(userRepository.findById(vendor.getId())).thenReturn(Optional.of(vendor));
            stubConversation();
            when(messageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(emailOutbox).enqueueVendorContractOffer(any(), any());
            when(contractRepository.save(any())).thenAnswer(i -> {
                VendorContract c = i.getArgument(0);
                c.setId(contractId);
                return c;
            });

            VendorContractResponse response = service.createContract(eventId, createRequest(), organizer);

            assertThat(response.getStatus()).isEqualTo(ContractStatus.DRAFT);
            assertThat(response.getVendorId()).isEqualTo(vendor.getId());

            ArgumentCaptor<VendorContract> captor = ArgumentCaptor.forClass(VendorContract.class);
            verify(contractRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ContractStatus.DRAFT);
            assertThat(captor.getValue().getOrganizer()).isEqualTo(organizer);
        }

        @Test
        @DisplayName("Throws UnauthorizedException when stranger tries to create contract")
        void throwsForStranger() {
            stubIsNotOrganizer(stranger);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));


            assertThatThrownBy(() -> service.createContract(eventId, createRequest(), stranger))
                    .isInstanceOf(UnauthorizedException.class);

            verify(contractRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws EventNotFoundException when event does not exist")
        void throwsWhenEventNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createContract(eventId, createRequest(), organizer))
                    .isInstanceOf(EventNotFoundException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when vendor not found")
        void throwsWhenVendorNotFound() {
            stubIsOrganizer(organizer);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(userRepository.findById(vendor.getId())).thenReturn(Optional.empty());


            assertThatThrownBy(() -> service.createContract(eventId, createRequest(), organizer))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Vendor not found");
        }
    }

    // ─── updateContract() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateContract()")
    class UpdateContract {

        @Test
        @DisplayName("Organizer can update a DRAFT contract")
        void organizerUpdatesDraftContract() {
            VendorContract contract = draftContract();
            stubIsOrganizer(organizer);
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(contractRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            UpdateContractRequest req = new UpdateContractRequest();
            req.setTitle("Updated Title");
            req.setAmount(new BigDecimal("50000.00"));

            VendorContractResponse response = service.updateContract(contractId, req, organizer);

            assertThat(response.getTitle()).isEqualTo("Updated Title");
            assertThat(response.getAmount()).isEqualByComparingTo("50000.00");
        }

        @Test
        @DisplayName("Throws when trying to update a SIGNED contract")
        void throwsWhenContractAlreadySigned() {
            VendorContract contract = draftContract();
            contract.setStatus(ContractStatus.SIGNED);
            stubIsOrganizer(organizer);
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

            assertThatThrownBy(() -> service.updateContract(contractId, new UpdateContractRequest(), organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT");
        }
    }

    // ─── signContract() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("signContract()")
    class SignContract {

        @Test
        @DisplayName("Vendor can sign a DRAFT contract — status becomes SIGNED")
        void vendorSignsContract() {
            VendorContract contract = draftContract();
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(contractRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.empty());

            VendorContractResponse response = service.signContract(contractId, vendor);

            assertThat(response.getStatus()).isEqualTo(ContractStatus.SIGNED);
            assertThat(response.getSignedAt()).isNotNull();
        }

        @Test
        @DisplayName("Auto-creates budget line item when a budget exists on signing")
        void wiresBudgetOnSign() {
            VendorContract contract = draftContract();
            EventBudget budget = EventBudget.builder().id(UUID.randomUUID()).event(event)
                    .totalBudget(new BigDecimal("1000000")).createdBy(organizer).build();

            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(contractRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(budgetRepository.findByEventId(eventId)).thenReturn(Optional.of(budget));
            when(lineItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.signContract(contractId, vendor);

            ArgumentCaptor<BudgetLineItem> captor = ArgumentCaptor.forClass(BudgetLineItem.class);
            verify(lineItemRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(LineItemStatus.PLANNED);
            assertThat(captor.getValue().getPlannedAmount()).isEqualByComparingTo("30000.00");
        }

        @Test
        @DisplayName("Throws UnauthorizedException when organizer tries to sign (not the vendor)")
        void throwsWhenOrganizerTriesToSign() {
            VendorContract contract = draftContract();
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

            assertThatThrownBy(() -> service.signContract(contractId, organizer))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Throws when contract is not DRAFT")
        void throwsWhenNotDraft() {
            VendorContract contract = draftContract();
            contract.setStatus(ContractStatus.SIGNED);
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

            assertThatThrownBy(() -> service.signContract(contractId, vendor))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─── terminateContract() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("terminateContract()")
    class TerminateContract {

        @Test
        @DisplayName("Organizer can terminate a SIGNED contract")
        void organizerTerminatesSignedContract() {
            VendorContract contract = draftContract();
            contract.setStatus(ContractStatus.SIGNED);
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(contractRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            VendorContractResponse response = service.terminateContract(contractId, organizer);

            assertThat(response.getStatus()).isEqualTo(ContractStatus.TERMINATED);
            assertThat(response.getTerminatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Vendor can also terminate a contract")
        void vendorTerminatesContract() {
            VendorContract contract = draftContract();
            contract.setStatus(ContractStatus.ACTIVE);
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(contractRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            VendorContractResponse response = service.terminateContract(contractId, vendor);

            assertThat(response.getStatus()).isEqualTo(ContractStatus.TERMINATED);
        }

        @Test
        @DisplayName("Throws UnauthorizedException when stranger tries to terminate")
        void throwsForStranger() {
            VendorContract contract = draftContract();
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

            assertThatThrownBy(() -> service.terminateContract(contractId, stranger))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Throws IllegalStateException when contract is already COMPLETED")
        void throwsWhenCompleted() {
            VendorContract contract = draftContract();
            contract.setStatus(ContractStatus.COMPLETED);
            when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

            assertThatThrownBy(() -> service.terminateContract(contractId, organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Completed");
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private VendorContract draftContract() {
        return VendorContract.builder()
                .id(contractId)
                .event(event)
                .organizer(organizer)
                .vendor(vendor)
                .title("Sound & Lighting")
                .amount(new BigDecimal("30000.00"))
                .status(ContractStatus.DRAFT)
                .build();
    }

    private CreateContractRequest createRequest() {
        CreateContractRequest req = new CreateContractRequest();
        req.setTitle("Sound & Lighting");
        req.setAmount(new BigDecimal("30000.00"));
        req.setVendorId(vendor.getId());
        return req;
    }

    private void stubIsOrganizer(User user) {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER))
                .thenReturn(true);
    }

    private void stubIsNotOrganizer(User user) {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER))
                .thenReturn(false);
    }

    private void stubConversation() {
        Conversation conv = Conversation.builder().id(UUID.randomUUID())
                .type(ConversationType.DIRECT).createdBy(organizer)
                .participants(new java.util.ArrayList<>()).build();
        when(conversationRepository.findDirectBetween(ConversationType.DIRECT, organizer.getId(), vendor.getId()))
                .thenReturn(Optional.of(conv));
    }
}
