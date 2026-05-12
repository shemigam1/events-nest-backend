package group.moniepoint.eventsnestserver.admin;

import group.moniepoint.eventsnestserver.admin.dto.request.CompleteAdminInvitationRequest;
import group.moniepoint.eventsnestserver.admin.dto.request.InviteAdminRequest;
import group.moniepoint.eventsnestserver.admin.dto.request.RejectEventRequest;
import group.moniepoint.eventsnestserver.admin.dto.request.UpdateUserStatusRequest;
import group.moniepoint.eventsnestserver.admin.dto.response.UserSummaryResponse;
import group.moniepoint.eventsnestserver.admin.kafka.AdminEventPublisher;
import group.moniepoint.eventsnestserver.admin.model.AdminInvitation;
import group.moniepoint.eventsnestserver.admin.repository.AdminInvitationRepository;
import group.moniepoint.eventsnestserver.admin.service.AdminService;
import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.email.EmailService;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.models.EventEditRequest;
import group.moniepoint.eventsnestserver.events.models.EventEditStatus;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.models.MembershipStatus;
import group.moniepoint.eventsnestserver.events.repository.EventEditRequestRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.auth.EmailAlreadyInUseException;
import group.moniepoint.eventsnestserver.exception.auth.InvitationTokenInvalidException;
import group.moniepoint.eventsnestserver.exception.event.EventNotPendingApprovalException;
import group.moniepoint.eventsnestserver.exception.event.NoPendingEventUpdateException;
import group.moniepoint.eventsnestserver.manager.dto.response.ManagerResponse;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorApplicationResponse;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplicationStatus;
import group.moniepoint.eventsnestserver.vendor.repository.VendorApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AdminService.
 *
 * Uses H2 in-memory database (JPA create-drop).
 * EmailService and AdminEventPublisher are mocked — they call SMTP/Kafka
 * which are unavailable in the test environment.
 * Redis and WebSocket dependencies are mocked by IntegrationTestConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AdminService Integration Tests")
@Import(IntegrationTestConfig.class)
class AdminServiceIntegrationTest {

    @Autowired private AdminService adminService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private EventEditRequestRepository editRequestRepository;
    @Autowired private AdminInvitationRepository invitationRepository;
    @Autowired private VendorApplicationRepository vendorApplicationRepository;

    @MockitoBean private EmailService emailService;
    @MockitoBean private AdminEventPublisher adminEventPublisher;

    private User organizer;
    private User candidate;
    private Events event;

    @BeforeEach
    void seed() {
        organizer = userRepository.save(User.builder()
                .id("organizer001")
                .firstName("Eve").lastName("Organizer")
                .email("eve@test.com").passwordHash("hashed").role(Role.USER)
                .build());

        candidate = userRepository.save(User.builder()
                .id("candidate001")
                .firstName("Mike").lastName("Candidate")
                .email("mike@test.com").passwordHash("hashed").role(Role.USER)
                .build());

        event = eventRepository.save(Events.builder()
                .title("Tech Summit 2025")
                .description("Annual tech conference")
                .venue("Lagos Convention Centre")
                .startTime(LocalDateTime.now().plusDays(30))
                .endTime(LocalDateTime.now().plusDays(30).plusHours(8))
                .status(EventStatus.PENDING_APPROVAL)
                .createdBy(organizer)
                .build());

        membershipRepository.save(EventMembership.builder()
                .events(event).user(organizer).role(EventRole.ORGANIZER)
                .status(MembershipStatus.ACTIVE).assignedBy(organizer)
                .build());
    }

    // ─── approveEvent() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approveEvent()")
    class ApproveEvent {

        @Test
        @DisplayName("Admin can approve a PENDING_APPROVAL event — status becomes PUBLISHED")
        void approvesEventSuccessfully() {
            EventsNestResponse<EventResponse> response = adminService.approveEvent(event.getId());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Event approved and published");
            assertThat(response.getData().getStatus()).isEqualTo(EventStatus.PUBLISHED);

            Events saved = eventRepository.findById(event.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(EventStatus.PUBLISHED);
            assertThat(saved.getRejectionReason()).isNull();
        }

        @Test
        @DisplayName("Throws EventNotPendingApprovalException when event is not PENDING_APPROVAL")
        void throwsForNonPendingEvent() {
            event.setStatus(EventStatus.DRAFT);
            eventRepository.save(event);

            assertThatThrownBy(() -> adminService.approveEvent(event.getId()))
                    .isInstanceOf(EventNotPendingApprovalException.class);
        }
    }

    // ─── rejectEvent() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectEvent()")
    class RejectEvent {

        @Test
        @DisplayName("Admin can reject an event with a reason — status reverts to DRAFT")
        void rejectsEventWithReason() {
            RejectEventRequest req = new RejectEventRequest();
            req.setReason("Venue not verified");

            EventsNestResponse<EventResponse> response = adminService.rejectEvent(event.getId(), req);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getStatus()).isEqualTo(EventStatus.DRAFT);
            assertThat(response.getData().getRejectionReason()).isEqualTo("Venue not verified");
        }

        @Test
        @DisplayName("Throws EventNotPendingApprovalException when event is already PUBLISHED")
        void throwsForPublishedEvent() {
            event.setStatus(EventStatus.PUBLISHED);
            eventRepository.save(event);

            RejectEventRequest req = new RejectEventRequest();
            req.setReason("Reason");

            assertThatThrownBy(() -> adminService.rejectEvent(event.getId(), req))
                    .isInstanceOf(EventNotPendingApprovalException.class);
        }
    }

    // ─── cancelEvent() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelEvent()")
    class CancelEvent {

        @Test
        @DisplayName("Admin can cancel any event regardless of current status")
        void adminCanCancelEvent() {
            event.setStatus(EventStatus.PUBLISHED);
            eventRepository.save(event);

            EventsNestResponse<EventResponse> response = adminService.cancelEvent(event.getId());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getStatus()).isEqualTo(EventStatus.CANCELLED);
        }

        @Test
        @DisplayName("Throws EventAlreadyCancelledException when event is already CANCELLED")
        void throwsWhenAlreadyCancelled() {
            event.setStatus(EventStatus.CANCELLED);
            eventRepository.save(event);

            assertThatThrownBy(() -> adminService.cancelEvent(event.getId()))
                    .isInstanceOf(group.moniepoint.eventsnestserver.exception.event.EventAlreadyCancelledException.class);
        }
    }

    // ─── approveEventUpdate() / rejectEventUpdate() ──────────────────────────────

    @Nested
    @DisplayName("approveEventUpdate() / rejectEventUpdate()")
    class EventUpdateReview {

        private EventEditRequest pendingEdit;

        @BeforeEach
        void createEditRequest() {
            event.setStatus(EventStatus.PUBLISHED);
            eventRepository.save(event);

            group.moniepoint.eventsnestserver.events.dto.EventEditProposedChanges changes =
                    new group.moniepoint.eventsnestserver.events.dto.EventEditProposedChanges(
                            "New description", null, null, null, null);

            pendingEdit = editRequestRepository.save(EventEditRequest.builder()
                    .event(event)
                    .submittedBy(organizer)
                    .proposedChanges(changes)
                    .status(EventEditStatus.PENDING)
                    .build());
        }

        @Test
        @DisplayName("Admin can approve an event edit request and apply the changes")
        void adminCanApproveEditRequest() {
            EventsNestResponse<EventResponse> response =
                    adminService.approveEventUpdate(pendingEdit.getId());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Event update approved and applied");

            EventEditRequest updated = editRequestRepository.findById(pendingEdit.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(EventEditStatus.APPROVED);

            Events updatedEvent = eventRepository.findById(event.getId()).orElseThrow();
            assertThat(updatedEvent.getDescription()).isEqualTo("New description");
        }

        @Test
        @DisplayName("Admin can reject an event edit request with a reason")
        void adminCanRejectEditRequest() {
            RejectEventRequest req = new RejectEventRequest();
            req.setReason("Changes not compliant");

            EventsNestResponse<EventResponse> response =
                    adminService.rejectEventUpdate(pendingEdit.getId(), req);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Event update rejected");

            EventEditRequest updated = editRequestRepository.findById(pendingEdit.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(EventEditStatus.REJECTED);
            assertThat(updated.getRejectionReason()).isEqualTo("Changes not compliant");
        }

        @Test
        @DisplayName("Throws NoPendingEventUpdateException for a non-existent edit request")
        void throwsForNonExistentRequest() {
            assertThatThrownBy(() -> adminService.approveEventUpdate(UUID.randomUUID()))
                    .isInstanceOf(NoPendingEventUpdateException.class);
        }
    }

    // ─── updateUserStatus() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateUserStatus()")
    class UpdateUserStatus {

        @Test
        @DisplayName("Admin can disable a user account")
        void adminCanDisableUser() {
            UpdateUserStatusRequest req = new UpdateUserStatusRequest();
            req.setEnabled(false);

            EventsNestResponse<UserSummaryResponse> response =
                    adminService.updateUserStatus(candidate.getId(), req);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("User account disabled");
            assertThat(response.getData().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Admin can re-enable a disabled user account")
        void adminCanEnableUser() {
            candidate.setEnabled(false);
            userRepository.save(candidate);

            UpdateUserStatusRequest req = new UpdateUserStatusRequest();
            req.setEnabled(true);

            EventsNestResponse<UserSummaryResponse> response =
                    adminService.updateUserStatus(candidate.getId(), req);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("User account enabled");
        }
    }

    // ─── inviteAdmin() / completeAdminInvitation() ───────────────────────────────

    @Nested
    @DisplayName("inviteAdmin() / completeAdminInvitation()")
    class AdminInvitation {

        @Test
        @DisplayName("Admin can invite a new admin user by email")
        void adminCanSendInvite() {
            InviteAdminRequest req = new InviteAdminRequest();
            req.setEmail("newadmin@test.com");

            EventsNestResponse<Void> response = adminService.inviteAdmin(req);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("newadmin@test.com");
            assertThat(invitationRepository.findByEmail("newadmin@test.com")).isPresent();
        }

        @Test
        @DisplayName("Throws EmailAlreadyInUseException when email is already registered")
        void throwsWhenEmailAlreadyExists() {
            InviteAdminRequest req = new InviteAdminRequest();
            req.setEmail(organizer.getEmail());

            assertThatThrownBy(() -> adminService.inviteAdmin(req))
                    .isInstanceOf(EmailAlreadyInUseException.class);
        }

        @Test
        @DisplayName("Admin invitation can be completed with valid token to create ADMIN role user")
        void canCompleteAdminInvitation() {
            String token = UUID.randomUUID().toString();
            invitationRepository.save(group.moniepoint.eventsnestserver.admin.model.AdminInvitation.builder()
                    .email("newadmin@example.com")
                    .token(token)
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build());

            CompleteAdminInvitationRequest req = new CompleteAdminInvitationRequest();
            req.setToken(token);
            req.setFirstName("New");
            req.setLastName("Admin");
            req.setPassword("SecurePass123!");

            EventsNestResponse<UserSummaryResponse> response =
                    adminService.completeAdminInvitation(req);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getRole()).isEqualTo("ADMIN");
            assertThat(userRepository.findByEmail("newadmin@example.com")).isPresent();
        }

        @Test
        @DisplayName("Throws InvitationTokenInvalidException for an expired token")
        void throwsForExpiredToken() {
            String token = UUID.randomUUID().toString();
            invitationRepository.save(group.moniepoint.eventsnestserver.admin.model.AdminInvitation.builder()
                    .email("expired@example.com")
                    .token(token)
                    .expiresAt(LocalDateTime.now().minusDays(1)) // expired
                    .build());

            CompleteAdminInvitationRequest req = new CompleteAdminInvitationRequest();
            req.setToken(token);
            req.setFirstName("Late");
            req.setLastName("User");
            req.setPassword("Pass1234!");

            assertThatThrownBy(() -> adminService.completeAdminInvitation(req))
                    .isInstanceOf(InvitationTokenInvalidException.class);
        }
    }

    // ─── approveVendorApplication() ──────────────────────────────────────────────

    @Nested
    @DisplayName("approveVendorApplication()")
    class ApproveVendorApplication {

        @Test
        @DisplayName("Admin can approve a vendor application for any event")
        void adminCanApproveVendorApplication() {
            VendorApplication app = vendorApplicationRepository.save(VendorApplication.builder()
                    .event(event).applicant(candidate)
                    .serviceType("Catering")
                    .proposedAmount(new BigDecimal("50000.00"))
                    .status(VendorApplicationStatus.PENDING)
                    .build());

            EventsNestResponse<VendorApplicationResponse> response =
                    adminService.approveVendorApplication(event.getId(), app.getId());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("approved");
            assertThat(response.getData().getStatus()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException for a non-existent application")
        void throwsForNonExistentApplication() {
            assertThatThrownBy(() ->
                    adminService.approveVendorApplication(event.getId(), UUID.randomUUID()))
                    .isInstanceOf(group.moniepoint.eventsnestserver.exception.ResourceNotFoundException.class);
        }
    }

    // ─── rejectVendorApplication() ───────────────────────────────────────────────

    @Nested
    @DisplayName("rejectVendorApplication()")
    class RejectVendorApplication {

        @Test
        @DisplayName("Admin can reject a vendor application for any event")
        void adminCanRejectVendorApplication() {
            VendorApplication app = vendorApplicationRepository.save(VendorApplication.builder()
                    .event(event).applicant(candidate)
                    .serviceType("Photography")
                    .proposedAmount(new BigDecimal("30000.00"))
                    .status(VendorApplicationStatus.PENDING)
                    .build());

            EventsNestResponse<VendorApplicationResponse> response =
                    adminService.rejectVendorApplication(event.getId(), app.getId());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getStatus()).isEqualTo("REJECTED");
        }
    }

    // ─── assignManagerToEvent() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("assignManagerToEvent()")
    class AssignManagerToEvent {

        @Test
        @DisplayName("Admin can assign any user as manager for any event")
        void adminCanAssignManager() {
            EventsNestResponse<ManagerResponse> response =
                    adminService.assignManagerToEvent(event.getId(), candidate.getId());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Manager assigned by admin");
            assertThat(response.getData().getUserId()).isEqualTo(candidate.getId());

            assertThat(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    event.getId(), candidate.getId(), EventRole.MANAGER)).isTrue();
        }

        @Test
        @DisplayName("Is idempotent when user is already a manager")
        void idempotentWhenAlreadyManager() {
            adminService.assignManagerToEvent(event.getId(), candidate.getId());

            EventsNestResponse<ManagerResponse> repeat =
                    adminService.assignManagerToEvent(event.getId(), candidate.getId());

            assertThat(repeat.getMessage()).isEqualTo("User is already a manager for this event");
        }
    }

    // ─── removeManagerFromEvent() ────────────────────────────────────────────────

    @Nested
    @DisplayName("removeManagerFromEvent()")
    class RemoveManagerFromEvent {

        @Test
        @DisplayName("Admin can remove a manager from any event")
        void adminCanRemoveManager() {
            membershipRepository.save(EventMembership.builder()
                    .events(event).user(candidate).role(EventRole.MANAGER)
                    .status(MembershipStatus.ACTIVE).assignedBy(organizer)
                    .build());

            EventsNestResponse<Void> response =
                    adminService.removeManagerFromEvent(event.getId(), candidate.getId());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Manager removed by admin");
            assertThat(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    event.getId(), candidate.getId(), EventRole.MANAGER)).isFalse();
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when user is not a manager for the event")
        void throwsWhenNotManager() {
            assertThatThrownBy(() ->
                    adminService.removeManagerFromEvent(event.getId(), candidate.getId()))
                    .isInstanceOf(group.moniepoint.eventsnestserver.exception.ResourceNotFoundException.class);
        }
    }
}
