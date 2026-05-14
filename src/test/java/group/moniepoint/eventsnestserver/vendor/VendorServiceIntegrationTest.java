package group.moniepoint.eventsnestserver.vendor;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.vendor.dto.request.ApplyAsVendorRequest;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorApplicationResponse;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorProfileResponse;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplicationStatus;
import group.moniepoint.eventsnestserver.vendor.repository.VendorApplicationRepository;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.vendor.service.VendorService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for VendorService.
 *
 * Uses an H2 in-memory database (schema from JPA create-drop).
 * Redis and WebSocket dependencies are mocked at the bean level.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Vendor Service Integration Tests")
@Import(IntegrationTestConfig.class)
class VendorServiceIntegrationTest {

    @Autowired private VendorService vendorService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private VendorApplicationRepository applicationRepository;

    private User organizer;
    private User manager;
    private User vendor;
    private Events event;

    @BeforeEach
    void seed() {
        organizer = userRepository.save(User.builder()
                .id("organizer001")
                .firstName("Eve")
                .lastName("Organizer")
                .email("eve@test.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .build());

        manager = userRepository.save(User.builder()
                .id("manager00001")
                .firstName("Mike")
                .lastName("Manager")
                .email("mike@test.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .build());

        vendor = userRepository.save(User.builder()
                .id("vendor000001")
                .firstName("Victor")
                .lastName("Vendor")
                .email("victor@test.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .build());

        event = eventRepository.save(Events.builder()
                .title("Tech Conference 2025")
                .description("Annual tech event")
                .venue("Lagos Convention Centre")
                .startTime(LocalDateTime.now().plusDays(10))
                .endTime(LocalDateTime.now().plusDays(11))
                .status(EventStatus.PUBLISHED)
                .createdBy(organizer)
                .build());

        // Wire organizer membership
        membershipRepository.save(EventMembership.builder()
                .events(event)
                .user(organizer)
                .role(EventRole.ORGANIZER)
                .assignedBy(organizer)
                .build());

        // Wire manager membership
        membershipRepository.save(EventMembership.builder()
                .events(event)
                .user(manager)
                .role(EventRole.MANAGER)
                .assignedBy(organizer)
                .build());
    }

    // ─── apply() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("apply()")
    class Apply {

        @Test
        @DisplayName("Any authenticated user can submit a vendor application")
        void anyUserCanApply() {
            VendorApplicationResponse response = vendorService.apply(event.getId(), cateringRequest(), vendor);

            assertThat(response.getId()).isNotNull();
            assertThat(response.getEventId()).isEqualTo(event.getId());
            assertThat(response.getApplicantId()).isEqualTo(vendor.getId());
            assertThat(response.getServiceType()).isEqualTo("Catering");
            assertThat(response.getStatus()).isEqualTo("PENDING");
            assertThat(response.getProposedAmount()).isEqualByComparingTo("50000.00");
        }

        @Test
        @DisplayName("Organizer can also apply as a vendor for their own event")
        void organizerCanApplyAsVendor() {
            VendorApplicationResponse response = vendorService.apply(event.getId(), cateringRequest(), organizer);

            assertThat(response.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Duplicate application for the same event + service type is rejected")
        void duplicateApplicationThrows() {
            vendorService.apply(event.getId(), cateringRequest(), vendor);

            assertThatThrownBy(() -> vendorService.apply(event.getId(), cateringRequest(), vendor))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already have a pending or accepted application");
        }

        @Test
        @DisplayName("Same vendor can apply for different service types on the same event")
        void canApplyForDifferentServiceTypes() {
            vendorService.apply(event.getId(), cateringRequest(), vendor);

            ApplyAsVendorRequest photoReq = new ApplyAsVendorRequest();
            photoReq.setServiceType("Photography");
            photoReq.setProposedAmount(new BigDecimal("30000.00"));

            VendorApplicationResponse second = vendorService.apply(event.getId(), photoReq, vendor);

            assertThat(second.getServiceType()).isEqualTo("Photography");
            assertThat(applicationRepository.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("Application is created with status PENDING by default")
        void defaultStatusIsPending() {
            VendorApplicationResponse response = vendorService.apply(event.getId(), cateringRequest(), vendor);

            assertThat(response.getStatus()).isEqualTo(VendorApplicationStatus.PENDING.name());
        }
    }

    // ─── listApplicationsForEvent() ───────────────────────────────────────────

    @Nested
    @DisplayName("listApplicationsForEvent()")
    class ListApplicationsForEvent {

        @Test
        @DisplayName("Organizer can list all applications for their event")
        void organizerCanListApplications() {
            vendorService.apply(event.getId(), cateringRequest(), vendor);

            List<VendorApplicationResponse> list =
                    vendorService.listApplicationsForEvent(event.getId(), null, organizer);

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getApplicantName()).isEqualTo("Victor Vendor");
        }

        @Test
        @DisplayName("Manager can list applications for their event")
        void managerCanListApplications() {
            vendorService.apply(event.getId(), cateringRequest(), vendor);

            List<VendorApplicationResponse> list =
                    vendorService.listApplicationsForEvent(event.getId(), null, manager);

            assertThat(list).hasSize(1);
        }

        @Test
        @DisplayName("Regular user who is not organizer or manager cannot list applications")
        void nonOrganizerCannotListApplications() {
            assertThatThrownBy(() ->
                    vendorService.listApplicationsForEvent(event.getId(), null, vendor))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Status filter returns only PENDING applications")
        void statusFilterReturnsPendingOnly() {
            vendorService.apply(event.getId(), cateringRequest(), vendor);

            ApplyAsVendorRequest photoReq = new ApplyAsVendorRequest();
            photoReq.setServiceType("Photography");
            photoReq.setProposedAmount(new BigDecimal("20000.00"));
            VendorApplicationResponse photoApp = vendorService.apply(event.getId(), photoReq, vendor);

            // Accept the photography application
            vendorService.accept(event.getId(), photoApp.getId(), organizer);

            List<VendorApplicationResponse> pending =
                    vendorService.listApplicationsForEvent(event.getId(), "PENDING", organizer);

            assertThat(pending).hasSize(1);
            assertThat(pending.get(0).getServiceType()).isEqualTo("Catering");
        }

        @Test
        @DisplayName("Returns empty list when event has no applications")
        void returnsEmptyWhenNoApplications() {
            List<VendorApplicationResponse> list =
                    vendorService.listApplicationsForEvent(event.getId(), null, organizer);

            assertThat(list).isEmpty();
        }
    }

    // ─── accept() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("accept()")
    class Accept {

        @Test
        @DisplayName("Organizer can accept a vendor application")
        void organizerCanAccept() {
            VendorApplicationResponse app = vendorService.apply(event.getId(), cateringRequest(), vendor);

            VendorApplicationResponse accepted = vendorService.accept(event.getId(), app.getId(), organizer);

            assertThat(accepted.getStatus()).isEqualTo("ACCEPTED");
            assertThat(accepted.getReviewedById()).isEqualTo(organizer.getId());
            assertThat(accepted.getReviewedAt()).isNotNull();
        }

        @Test
        @DisplayName("Manager can accept a vendor application")
        void managerCanAccept() {
            VendorApplicationResponse app = vendorService.apply(event.getId(), cateringRequest(), vendor);

            VendorApplicationResponse accepted = vendorService.accept(event.getId(), app.getId(), manager);

            assertThat(accepted.getStatus()).isEqualTo("ACCEPTED");
            assertThat(accepted.getReviewedById()).isEqualTo(manager.getId());
        }

        @Test
        @DisplayName("Non-organizer/manager cannot accept an application")
        void nonOrganizerCannotAccept() {
            VendorApplicationResponse app = vendorService.apply(event.getId(), cateringRequest(), vendor);

            assertThatThrownBy(() -> vendorService.accept(event.getId(), app.getId(), vendor))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Accepting a non-existent application throws ResourceNotFoundException")
        void throwsForNonExistentApplication() {
            assertThatThrownBy(() -> vendorService.accept(event.getId(), UUID.randomUUID(), organizer))
                    .isInstanceOf(group.moniepoint.eventsnestserver.exception.ResourceNotFoundException.class);
        }
    }

    // ─── reject() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reject()")
    class Reject {

        @Test
        @DisplayName("Organizer can reject a vendor application")
        void organizerCanReject() {
            VendorApplicationResponse app = vendorService.apply(event.getId(), cateringRequest(), vendor);

            VendorApplicationResponse rejected = vendorService.reject(event.getId(), app.getId(), organizer);

            assertThat(rejected.getStatus()).isEqualTo("REJECTED");
            assertThat(rejected.getReviewedById()).isEqualTo(organizer.getId());
        }

        @Test
        @DisplayName("Manager can reject a vendor application")
        void managerCanReject() {
            VendorApplicationResponse app = vendorService.apply(event.getId(), cateringRequest(), vendor);

            VendorApplicationResponse rejected = vendorService.reject(event.getId(), app.getId(), manager);

            assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("Non-organizer/manager cannot reject an application")
        void nonOrganizerCannotReject() {
            VendorApplicationResponse app = vendorService.apply(event.getId(), cateringRequest(), vendor);

            assertThatThrownBy(() -> vendorService.reject(event.getId(), app.getId(), vendor))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── listMyApplications() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("listMyApplications()")
    class ListMyApplications {

        @Test
        @DisplayName("Returns all applications submitted by the caller")
        void returnsOwnApplications() {
            vendorService.apply(event.getId(), cateringRequest(), vendor);

            ApplyAsVendorRequest photoReq = new ApplyAsVendorRequest();
            photoReq.setServiceType("Photography");
            photoReq.setProposedAmount(new BigDecimal("20000.00"));
            vendorService.apply(event.getId(), photoReq, vendor);

            List<VendorApplicationResponse> mine = vendorService.listMyApplications(vendor);

            assertThat(mine).hasSize(2);
        }

        @Test
        @DisplayName("Does not return other users' applications")
        void doesNotReturnOtherUsersApplications() {
            vendorService.apply(event.getId(), cateringRequest(), vendor);

            List<VendorApplicationResponse> organizerApps = vendorService.listMyApplications(organizer);

            assertThat(organizerApps).isEmpty();
        }

        @Test
        @DisplayName("Returns empty list when caller has no applications")
        void returnsEmptyForNewUser() {
            assertThat(vendorService.listMyApplications(vendor)).isEmpty();
        }
    }

    // ─── getVendorProfile() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getVendorProfile()")
    class GetVendorProfile {

        @Test
        @DisplayName("Any authenticated user can view a vendor's profile")
        void anyUserCanViewVendorProfile() {
            vendor.setVendorServiceType("Catering");
            vendor.setVendorProfileDescription("Professional event catering");
            vendor.setVendorVerified(true);
            userRepository.save(vendor);

            VendorProfileResponse profile = vendorService.getVendorProfile(vendor.getId(), organizer);

            assertThat(profile.getVendorId()).isEqualTo(vendor.getId());
            assertThat(profile.getVendorName()).isEqualTo("Victor Vendor");
            assertThat(profile.getServiceType()).isEqualTo("Catering");
            assertThat(profile.isVendorVerified()).isTrue();
        }

        @Test
        @DisplayName("Manager can view vendor profile without restriction")
        void managerCanViewVendorProfile() {
            vendor.setVendorServiceType("Photography");
            userRepository.save(vendor);

            VendorProfileResponse profile = vendorService.getVendorProfile(vendor.getId(), manager);

            assertThat(profile.getVendorId()).isEqualTo(vendor.getId());
            assertThat(profile.getServiceType()).isEqualTo("Photography");
        }

        @Test
        @DisplayName("Vendor can view their own profile")
        void vendorCanViewOwnProfile() {
            vendor.setVendorServiceType("Decoration");
            userRepository.save(vendor);

            VendorProfileResponse profile = vendorService.getVendorProfile(vendor.getId(), vendor);

            assertThat(profile.getVendorId()).isEqualTo(vendor.getId());
            assertThat(profile.getServiceType()).isEqualTo("Decoration");
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when vendor does not exist")
        void throwsWhenVendorNotFound() {
            assertThatThrownBy(() -> vendorService.getVendorProfile("nonexistent123", organizer))
                    .isInstanceOf(group.moniepoint.eventsnestserver.exception.ResourceNotFoundException.class);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private ApplyAsVendorRequest cateringRequest() {
        ApplyAsVendorRequest req = new ApplyAsVendorRequest();
        req.setServiceType("Catering");
        req.setDescription("Full catering service for up to 500 guests");
        req.setProposedAmount(new BigDecimal("50000.00"));
        return req;
    }
}
