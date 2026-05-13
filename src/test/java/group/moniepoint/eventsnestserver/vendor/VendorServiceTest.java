package group.moniepoint.eventsnestserver.vendor;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.model.VendorVerificationStatus;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.vendor.dto.request.ApplyForVendorVerificationRequest;
import group.moniepoint.eventsnestserver.vendor.dto.request.ApplyAsVendorRequest;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorApplicationResponse;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplicationStatus;
import group.moniepoint.eventsnestserver.audit.publisher.AuditEventPublisher;
import group.moniepoint.eventsnestserver.vendor.repository.VendorApplicationRepository;
import group.moniepoint.eventsnestserver.vendor.repository.VendorRatingRepository;
import group.moniepoint.eventsnestserver.vendor.service.VendorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VendorServiceImpl.
 *
 * All dependencies are mocked — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VendorService Unit Tests")
class VendorServiceTest {

    @Mock private VendorApplicationRepository applicationRepository;
    @Mock private VendorRatingRepository ratingRepository;
    @Mock private EventRespository eventRepository;
    @Mock private EventMembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditEventPublisher auditEventPublisher;

    private VendorServiceImpl vendorService;

    private User organizer;
    private User manager;
    private User vendor;
    private Events event;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        vendorService = new VendorServiceImpl(
                applicationRepository, ratingRepository, eventRepository, membershipRepository, userRepository, auditEventPublisher);

        organizer = user("organizer0001", "Eve", "Organizer");
        manager   = user("manager00001", "Mike", "Manager");
        vendor    = user("vendor000001", "Victor", "Vendor");

        eventId = UUID.randomUUID();
        event   = event(eventId, "Tech Conference", organizer);
    }

    // ─── apply() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("apply()")
    class Apply {

        @Test
        @DisplayName("Saves a new PENDING application and returns the mapped response")
        void savesNewPendingApplication() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(applicationRepository.existsByEventIdAndApplicantIdAndServiceType(
                    eventId, vendor.getId(), "Catering")).thenReturn(false);
            when(applicationRepository.save(any(VendorApplication.class)))
                    .thenAnswer(inv -> {
                        VendorApplication a = inv.getArgument(0);
                        a.setId(UUID.randomUUID());
                        return a;
                    });

            VendorApplicationResponse response =
                    vendorService.apply(eventId, cateringRequest(), vendor);

            assertThat(response.getStatus()).isEqualTo("PENDING");
            assertThat(response.getServiceType()).isEqualTo("Catering");
            assertThat(response.getApplicantId()).isEqualTo(vendor.getId());
            assertThat(response.getEventId()).isEqualTo(eventId);

            ArgumentCaptor<VendorApplication> captor =
                    ArgumentCaptor.forClass(VendorApplication.class);
            verify(applicationRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(VendorApplicationStatus.PENDING);
        }

        @Test
        @DisplayName("Throws IllegalStateException when a duplicate application is detected")
        void throwsOnDuplicateApplication() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(applicationRepository.existsByEventIdAndApplicantIdAndServiceType(
                    eventId, vendor.getId(), "Catering")).thenReturn(true);

            assertThatThrownBy(() -> vendorService.apply(eventId, cateringRequest(), vendor))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already have a pending or accepted application");

            verify(applicationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when event does not exist")
        void throwsWhenEventNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> vendorService.apply(eventId, cateringRequest(), vendor))
                    .isInstanceOf(group.moniepoint.eventsnestserver.exception.event.EventNotFoundException.class);

            verify(applicationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Stores proposed amount and description from the request")
        void storesAmountAndDescription() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(applicationRepository.existsByEventIdAndApplicantIdAndServiceType(
                    eventId, vendor.getId(), "Photography")).thenReturn(false);
            when(applicationRepository.save(any())).thenAnswer(inv -> {
                VendorApplication a = inv.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });

            ApplyAsVendorRequest req = new ApplyAsVendorRequest();
            req.setServiceType("Photography");
            req.setDescription("Professional event photography");
            req.setProposedAmount(new BigDecimal("25000.00"));

            VendorApplicationResponse response = vendorService.apply(eventId, req, vendor);

            assertThat(response.getDescription()).isEqualTo("Professional event photography");
            assertThat(response.getProposedAmount()).isEqualByComparingTo("25000.00");
        }
    }

    // ─── listApplicationsForEvent() ──────────────────────────────────────────

    @Nested
    @DisplayName("listApplicationsForEvent()")
    class ListApplicationsForEvent {

        @Test
        @DisplayName("Returns all applications when no status filter is provided")
        void returnsAllApplicationsWithoutFilter() {
            stubOrganizer();
            VendorApplication app = application(vendor, event, "Catering", VendorApplicationStatus.PENDING);
            when(applicationRepository.findAllByEventIdOrderByCreatedAtDesc(eventId))
                    .thenReturn(List.of(app));

            List<VendorApplicationResponse> result =
                    vendorService.listApplicationsForEvent(eventId, null, organizer);

            assertThat(result).hasSize(1);
            verify(applicationRepository).findAllByEventIdOrderByCreatedAtDesc(eventId);
            verify(applicationRepository, never())
                    .findAllByEventIdAndStatusOrderByCreatedAtDesc(any(), any());
        }

        @Test
        @DisplayName("Filters by status when status param is provided")
        void filtersWhenStatusProvided() {
            stubOrganizer();
            VendorApplication app = application(vendor, event, "Catering", VendorApplicationStatus.PENDING);
            when(applicationRepository.findAllByEventIdAndStatusOrderByCreatedAtDesc(
                    eventId, VendorApplicationStatus.PENDING)).thenReturn(List.of(app));

            List<VendorApplicationResponse> result =
                    vendorService.listApplicationsForEvent(eventId, "PENDING", organizer);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Throws UnauthorizedException when caller is neither organizer nor manager")
        void throwsWhenNotOrganizerOrManager() {
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    eventId, vendor.getId(), EventRole.ORGANIZER)).thenReturn(false);
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    eventId, vendor.getId(), EventRole.MANAGER)).thenReturn(false);

            assertThatThrownBy(() ->
                    vendorService.listApplicationsForEvent(eventId, null, vendor))
                    .isInstanceOf(UnauthorizedException.class);

            verify(applicationRepository, never()).findAllByEventIdOrderByCreatedAtDesc(any());
        }

        @Test
        @DisplayName("Manager is allowed to list applications for their event")
        void managerCanListApplications() {
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    eventId, manager.getId(), EventRole.ORGANIZER)).thenReturn(false);
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    eventId, manager.getId(), EventRole.MANAGER)).thenReturn(true);
            when(applicationRepository.findAllByEventIdOrderByCreatedAtDesc(eventId))
                    .thenReturn(List.of());

            List<VendorApplicationResponse> result =
                    vendorService.listApplicationsForEvent(eventId, null, manager);

            assertThat(result).isEmpty();
            verify(applicationRepository).findAllByEventIdOrderByCreatedAtDesc(eventId);
        }
    }

    // ─── accept() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("accept()")
    class Accept {

        @Test
        @DisplayName("Sets status to ACCEPTED and records reviewer")
        void setsAcceptedStatus() {
            stubOrganizer();
            UUID appId = UUID.randomUUID();
            VendorApplication app = application(vendor, event, "Catering", VendorApplicationStatus.PENDING);
            app.setId(appId);

            when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            VendorApplicationResponse response = vendorService.accept(eventId, appId, organizer);

            assertThat(response.getStatus()).isEqualTo("ACCEPTED");
            assertThat(response.getReviewedById()).isEqualTo(organizer.getId());
            assertThat(response.getReviewedAt()).isNotNull();
        }

        @Test
        @DisplayName("Manager can accept an application")
        void managerCanAccept() {
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    eventId, manager.getId(), EventRole.ORGANIZER)).thenReturn(false);
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    eventId, manager.getId(), EventRole.MANAGER)).thenReturn(true);

            UUID appId = UUID.randomUUID();
            VendorApplication app = application(vendor, event, "Catering", VendorApplicationStatus.PENDING);
            app.setId(appId);

            when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            VendorApplicationResponse response = vendorService.accept(eventId, appId, manager);

            assertThat(response.getStatus()).isEqualTo("ACCEPTED");
            assertThat(response.getReviewedById()).isEqualTo(manager.getId());
        }

        @Test
        @DisplayName("Throws UnauthorizedException when caller is not organizer or manager")
        void throwsUnauthorized() {
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    eventId, vendor.getId(), EventRole.ORGANIZER)).thenReturn(false);
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    eventId, vendor.getId(), EventRole.MANAGER)).thenReturn(false);

            assertThatThrownBy(() -> vendorService.accept(eventId, UUID.randomUUID(), vendor))
                    .isInstanceOf(UnauthorizedException.class);

            verify(applicationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when application does not exist")
        void throwsWhenApplicationNotFound() {
            stubOrganizer();
            UUID missingId = UUID.randomUUID();
            when(applicationRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> vendorService.accept(eventId, missingId, organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when application belongs to a different event")
        void throwsWhenApplicationBelongsToDifferentEvent() {
            stubOrganizer();
            UUID appId = UUID.randomUUID();
            Events otherEvent = event(UUID.randomUUID(), "Other Event", organizer);
            VendorApplication app = application(vendor, otherEvent, "Catering", VendorApplicationStatus.PENDING);
            app.setId(appId);

            when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

            assertThatThrownBy(() -> vendorService.accept(eventId, appId, organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── reject() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reject()")
    class Reject {

        @Test
        @DisplayName("Sets status to REJECTED and records reviewer")
        void setsRejectedStatus() {
            stubOrganizer();
            UUID appId = UUID.randomUUID();
            VendorApplication app = application(vendor, event, "Catering", VendorApplicationStatus.PENDING);
            app.setId(appId);

            when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            VendorApplicationResponse response = vendorService.reject(eventId, appId, organizer);

            assertThat(response.getStatus()).isEqualTo("REJECTED");
            assertThat(response.getReviewedById()).isEqualTo(organizer.getId());
            assertThat(response.getReviewedAt()).isNotNull();
        }

        @Test
        @DisplayName("Throws UnauthorizedException when caller is not organizer or manager")
        void throwsUnauthorized() {
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    eventId, vendor.getId(), EventRole.ORGANIZER)).thenReturn(false);
            when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                    eventId, vendor.getId(), EventRole.MANAGER)).thenReturn(false);

            assertThatThrownBy(() -> vendorService.reject(eventId, UUID.randomUUID(), vendor))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── listMyApplications() ────────────────────────────────────────────────

    @Nested
    @DisplayName("listMyApplications()")
    class ListMyApplications {

        @Test
        @DisplayName("Returns only applications belonging to the caller")
        void returnsOwnApplications() {
            VendorApplication a1 = application(vendor, event, "Catering",    VendorApplicationStatus.PENDING);
            VendorApplication a2 = application(vendor, event, "Photography", VendorApplicationStatus.ACCEPTED);
            when(applicationRepository.findAllByApplicantIdOrderByCreatedAtDesc(vendor.getId()))
                    .thenReturn(List.of(a1, a2));

            List<VendorApplicationResponse> result = vendorService.listMyApplications(vendor);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(VendorApplicationResponse::getServiceType)
                    .containsExactlyInAnyOrder("Catering", "Photography");
        }

        @Test
        @DisplayName("Returns empty list when caller has no applications")
        void returnsEmptyList() {
            when(applicationRepository.findAllByApplicantIdOrderByCreatedAtDesc(organizer.getId()))
                    .thenReturn(List.of());

            assertThat(vendorService.listMyApplications(organizer)).isEmpty();
        }

        @Test
        @DisplayName("Does not check organizer/manager role — any user can view their own applications")
        void anyUserCanViewOwnApplications() {
            when(applicationRepository.findAllByApplicantIdOrderByCreatedAtDesc(vendor.getId()))
                    .thenReturn(List.of());

            vendorService.listMyApplications(vendor);

            // No membership check should occur
            verify(membershipRepository, never())
                    .existsByEventsIdAndUserIdAndRole(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("vendor verification")
    class VendorVerification {

        @Test
        @DisplayName("Submitting verification marks user as PENDING")
        void applyForVerificationMarksPending() {
            when(userRepository.save(vendor)).thenAnswer(inv -> inv.getArgument(0));

            var response = vendorService.applyForVerification(verificationRequest(), vendor);

            assertThat(response.getStatus()).isEqualTo(VendorVerificationStatus.PENDING.name());
            assertThat(response.isVendorVerified()).isFalse();
            assertThat(response.getServiceType()).isEqualTo("Catering");
            assertThat(response.getSubmittedAt()).isNotNull();
            verify(userRepository).save(vendor);
        }

        @Test
        @DisplayName("Admin approval marks user as verified")
        void approveVerificationMarksVerified() {
            vendor.setVendorVerificationStatus(VendorVerificationStatus.PENDING);
            when(userRepository.findById(vendor.getId())).thenReturn(Optional.of(vendor));
            when(userRepository.save(vendor)).thenAnswer(inv -> inv.getArgument(0));

            var response = vendorService.approveVerification(vendor.getId());

            assertThat(response.isVendorVerified()).isTrue();
            assertThat(response.getStatus()).isEqualTo(VendorVerificationStatus.VERIFIED.name());
            assertThat(response.getVerifiedAt()).isNotNull();
        }

        @Test
        @DisplayName("Admin rejection records reason")
        void rejectVerificationRecordsReason() {
            vendor.setVendorVerificationStatus(VendorVerificationStatus.PENDING);
            when(userRepository.findById(vendor.getId())).thenReturn(Optional.of(vendor));
            when(userRepository.save(vendor)).thenAnswer(inv -> inv.getArgument(0));

            var response = vendorService.rejectVerification(vendor.getId(), "Documents are incomplete");

            assertThat(response.isVendorVerified()).isFalse();
            assertThat(response.getStatus()).isEqualTo(VendorVerificationStatus.REJECTED.name());
            assertThat(response.getRejectionReason()).isEqualTo("Documents are incomplete");
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User user(String id, String first, String last) {
        return User.builder()
                .id(id).firstName(first).lastName(last)
                .email(first.toLowerCase() + "@test.com")
                .passwordHash("hashed").role(Role.USER)
                .build();
    }

    private Events event(UUID id, String title, User createdBy) {
        Events e = new Events();
        e.setId(id);
        e.setTitle(title);
        e.setVenue("Test Venue");
        e.setStartTime(LocalDateTime.now().plusDays(5));
        e.setEndTime(LocalDateTime.now().plusDays(6));
        e.setStatus(EventStatus.PUBLISHED);
        e.setCreatedBy(createdBy);
        return e;
    }

    private VendorApplication application(User applicant, Events event,
                                          String serviceType, VendorApplicationStatus status) {
        VendorApplication a = new VendorApplication();
        a.setId(UUID.randomUUID());
        a.setApplicant(applicant);
        a.setEvent(event);
        a.setServiceType(serviceType);
        a.setProposedAmount(new BigDecimal("50000.00"));
        a.setStatus(status);
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }

    private ApplyAsVendorRequest cateringRequest() {
        ApplyAsVendorRequest req = new ApplyAsVendorRequest();
        req.setServiceType("Catering");
        req.setDescription("Full catering service");
        req.setProposedAmount(new BigDecimal("50000.00"));
        return req;
    }

    private ApplyForVendorVerificationRequest verificationRequest() {
        ApplyForVendorVerificationRequest req = new ApplyForVendorVerificationRequest();
        req.setServiceType("Catering");
        req.setDescription("Licensed catering vendor");
        return req;
    }

    private void stubOrganizer() {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, organizer.getId(), EventRole.ORGANIZER)).thenReturn(true);
    }
}
