package group.moniepoint.eventsnestserver.vendor;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.vendor.dto.request.CreateInquiryRequest;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorInquiryResponse;
import group.moniepoint.eventsnestserver.vendor.model.InquiryStatus;
import group.moniepoint.eventsnestserver.vendor.repository.VendorInquiryRepository;
import group.moniepoint.eventsnestserver.vendor.service.VendorInquiryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("VendorInquiryService Integration Tests")
@Import(IntegrationTestConfig.class)
class VendorInquiryServiceIntegrationTest {

    @Autowired private VendorInquiryService inquiryService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private VendorInquiryRepository inquiryRepository;

    private User organizer;
    private User vendor;
    private User stranger;
    private Events event;

    @BeforeEach
    void seed() {
        organizer = userRepository.save(User.builder()
                .id("org001vi").firstName("Alice").lastName("Org")
                .email("alice-vi@test.com").passwordHash("hashed").role(Role.USER).build());

        vendor = userRepository.save(User.builder()
                .id("ven001vi").firstName("Bob").lastName("Vendor")
                .email("bob-vi@test.com").passwordHash("hashed").role(Role.USER).build());

        stranger = userRepository.save(User.builder()
                .id("str001vi").firstName("Chuck").lastName("Stranger")
                .email("chuck-vi@test.com").passwordHash("hashed").role(Role.USER).build());

        event = eventRepository.save(Events.builder()
                .title("Inquiry Test Event").description("Integration test").venue("Test Venue")
                .startTime(LocalDateTime.now().plusDays(30))
                .endTime(LocalDateTime.now().plusDays(30).plusHours(8))
                .status(EventStatus.DRAFT).createdBy(organizer).build());

        membershipRepository.save(EventMembership.builder()
                .events(event).user(organizer).role(EventRole.ORGANIZER).assignedBy(organizer).build());
    }

    // ─── sendInquiry() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendInquiry()")
    class SendInquiry {

        @Test
        @DisplayName("Organizer sends inquiry — persisted as PENDING with auto-created DM conversation")
        void organizerSendsInquiry() {
            VendorInquiryResponse response = inquiryService.sendInquiry(event.getId(), createRequest(), organizer);

            assertThat(response.getId()).isNotNull();
            assertThat(response.getStatus()).isEqualTo(InquiryStatus.PENDING);
            assertThat(response.getVendorId()).isEqualTo(vendor.getId());
            assertThat(response.getConversationId()).isNotNull();
            assertThat(inquiryRepository.existsById(response.getId())).isTrue();
        }

        @Test
        @DisplayName("Second inquiry reuses the same DM conversation")
        void reusesDmConversation() {
            VendorInquiryResponse first  = inquiryService.sendInquiry(event.getId(), createRequest(), organizer);

            // Create a second inquiry on a different event to test conversation reuse
            Events event2 = eventRepository.save(Events.builder()
                    .title("Event 2").description("test").venue("Venue 2")
                    .startTime(LocalDateTime.now().plusDays(60))
                    .endTime(LocalDateTime.now().plusDays(60).plusHours(8))
                    .status(EventStatus.DRAFT).createdBy(organizer).build());
            membershipRepository.save(EventMembership.builder()
                    .events(event2).user(organizer).role(EventRole.ORGANIZER).assignedBy(organizer).build());

            VendorInquiryResponse second = inquiryService.sendInquiry(event2.getId(), createRequest(), organizer);

            assertThat(second.getConversationId()).isEqualTo(first.getConversationId());
        }

        @Test
        @DisplayName("Throws IllegalStateException when duplicate inquiry is sent to same vendor for same event")
        void throwsOnDuplicate() {
            inquiryService.sendInquiry(event.getId(), createRequest(), organizer);

            assertThatThrownBy(() -> inquiryService.sendInquiry(event.getId(), createRequest(), organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been sent");
        }

        @Test
        @DisplayName("Throws UnauthorizedException when stranger sends inquiry")
        void throwsForStranger() {
            assertThatThrownBy(() -> inquiryService.sendInquiry(event.getId(), createRequest(), stranger))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── listSentInquiries() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("listSentInquiries()")
    class ListSent {

        @Test
        @DisplayName("Organizer sees all inquiries sent for their event")
        void organizerSeesOwnInquiries() {
            inquiryService.sendInquiry(event.getId(), createRequest(), organizer);

            List<VendorInquiryResponse> list = inquiryService.listSentInquiries(event.getId(), organizer);

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getOrganizerId()).isEqualTo(organizer.getId());
        }

        @Test
        @DisplayName("Throws UnauthorizedException when stranger lists inquiries")
        void throwsForStranger() {
            assertThatThrownBy(() -> inquiryService.listSentInquiries(event.getId(), stranger))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── listReceivedInquiries() ──────────────────────────────────────────────

    @Nested
    @DisplayName("listReceivedInquiries()")
    class ListReceived {

        @Test
        @DisplayName("Vendor sees all inquiries received")
        void vendorSeesReceivedInquiries() {
            inquiryService.sendInquiry(event.getId(), createRequest(), organizer);

            List<VendorInquiryResponse> list = inquiryService.listReceivedInquiries(vendor);

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getVendorId()).isEqualTo(vendor.getId());
        }
    }

    // ─── closeInquiry() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("closeInquiry()")
    class CloseInquiry {

        @Test
        @DisplayName("Organizer closes inquiry — status becomes CLOSED")
        void organizerClosesInquiry() {
            VendorInquiryResponse sent = inquiryService.sendInquiry(event.getId(), createRequest(), organizer);

            VendorInquiryResponse closed = inquiryService.closeInquiry(sent.getId(), organizer);

            assertThat(closed.getStatus()).isEqualTo(InquiryStatus.CLOSED);
        }

        @Test
        @DisplayName("Throws UnauthorizedException when vendor tries to close")
        void throwsForVendor() {
            VendorInquiryResponse sent = inquiryService.sendInquiry(event.getId(), createRequest(), organizer);

            assertThatThrownBy(() -> inquiryService.closeInquiry(sent.getId(), vendor))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private CreateInquiryRequest createRequest() {
        CreateInquiryRequest req = new CreateInquiryRequest();
        req.setVendorId(vendor.getId());
        req.setMessage("We need AV services for our event");
        req.setServiceType("AV");
        return req;
    }
}
