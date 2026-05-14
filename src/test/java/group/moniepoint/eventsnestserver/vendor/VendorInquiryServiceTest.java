package group.moniepoint.eventsnestserver.vendor;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.chat.model.Conversation;
import group.moniepoint.eventsnestserver.chat.model.ConversationType;
import group.moniepoint.eventsnestserver.chat.repository.ConversationRepository;
import group.moniepoint.eventsnestserver.chat.repository.MessageRepository;
import group.moniepoint.eventsnestserver.email.EmailOutbox;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.vendor.dto.request.CreateInquiryRequest;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorInquiryResponse;
import group.moniepoint.eventsnestserver.vendor.model.InquiryStatus;
import group.moniepoint.eventsnestserver.vendor.model.VendorInquiry;
import group.moniepoint.eventsnestserver.vendor.repository.VendorInquiryRepository;
import group.moniepoint.eventsnestserver.vendor.service.VendorInquiryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
@DisplayName("VendorInquiryService Unit Tests")
class VendorInquiryServiceTest {

    @Mock private VendorInquiryRepository inquiryRepository;
    @Mock private EventRespository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventMembershipRepository membershipRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private EmailOutbox emailOutbox;

    private VendorInquiryServiceImpl service;

    private User organizer;
    private User vendor;
    private Events event;
    private UUID eventId;
    private UUID inquiryId;

    @BeforeEach
    void setUp() {
        service = new VendorInquiryServiceImpl(
                inquiryRepository, eventRepository, userRepository,
                membershipRepository, conversationRepository,
                messageRepository, emailOutbox);

        organizer = User.builder().id("org001").firstName("Alice").lastName("Org")
                .email("alice@test.com").role(Role.USER).build();
        vendor = User.builder().id("ven001").firstName("Bob").lastName("Vendor")
                .email("bob@test.com").role(Role.USER).build();

        eventId = UUID.randomUUID();
        inquiryId = UUID.randomUUID();
        event = Events.builder().id(eventId).title("Test Event").createdBy(organizer).build();
    }

    // ─── sendInquiry() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendInquiry()")
    class SendInquiry {

        @Test
        @DisplayName("Organizer sends inquiry — creates inquiry and reuses existing DM conversation")
        void organizerSendsInquiry() {
            Conversation existingDm = Conversation.builder().id(UUID.randomUUID())
                    .type(ConversationType.DIRECT).createdBy(organizer).participants(new ArrayList<>()).build();

            stubIsOrganizer(organizer);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(userRepository.findById(vendor.getId())).thenReturn(Optional.of(vendor));
            when(inquiryRepository.existsByEventIdAndOrganizerIdAndVendorId(eventId, organizer.getId(), vendor.getId()))
                    .thenReturn(false);
            when(conversationRepository.findDirectBetween(ConversationType.DIRECT, organizer.getId(), vendor.getId()))
                    .thenReturn(Optional.of(existingDm));
            when(inquiryRepository.save(any())).thenAnswer(i -> {
                VendorInquiry inq = i.getArgument(0);
                inq.setId(inquiryId);
                return inq;
            });
            when(messageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(emailOutbox).enqueueVendorInquiry(any(), any());

            VendorInquiryResponse response = service.sendInquiry(eventId, createRequest(), organizer);

            assertThat(response.getStatus()).isEqualTo(InquiryStatus.PENDING);
            assertThat(response.getVendorId()).isEqualTo(vendor.getId());
            assertThat(response.getConversationId()).isEqualTo(existingDm.getId());
            verify(conversationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Creates a new DM conversation when none exists between organizer and vendor")
        void createsNewDmWhenNoneExists() {
            Conversation savedConv = Conversation.builder().id(UUID.randomUUID())
                    .type(ConversationType.DIRECT).createdBy(organizer).participants(new ArrayList<>()).build();

            stubIsOrganizer(organizer);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(userRepository.findById(vendor.getId())).thenReturn(Optional.of(vendor));
            when(inquiryRepository.existsByEventIdAndOrganizerIdAndVendorId(eventId, organizer.getId(), vendor.getId()))
                    .thenReturn(false);
            when(conversationRepository.findDirectBetween(ConversationType.DIRECT, organizer.getId(), vendor.getId()))
                    .thenReturn(Optional.empty());
            when(conversationRepository.save(any())).thenReturn(savedConv);
            when(inquiryRepository.save(any())).thenAnswer(i -> {
                VendorInquiry inq = i.getArgument(0);
                inq.setId(inquiryId);
                return inq;
            });
            when(messageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(emailOutbox).enqueueVendorInquiry(any(), any());

            service.sendInquiry(eventId, createRequest(), organizer);

            verify(conversationRepository, atLeastOnce()).save(any());
        }

        @Test
        @DisplayName("Throws when duplicate inquiry already exists")
        void throwsOnDuplicateInquiry() {
            stubIsOrganizer(organizer);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(userRepository.findById(vendor.getId())).thenReturn(Optional.of(vendor));
            when(inquiryRepository.existsByEventIdAndOrganizerIdAndVendorId(eventId, organizer.getId(), vendor.getId()))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.sendInquiry(eventId, createRequest(), organizer))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been sent");
        }

        @Test
        @DisplayName("Throws UnauthorizedException when stranger tries to send inquiry")
        void throwsForStranger() {
            stubIsNotOrganizer(vendor);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> service.sendInquiry(eventId, createRequest(), vendor))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Throws EventNotFoundException when event does not exist")
        void throwsWhenEventNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.sendInquiry(eventId, createRequest(), organizer))
                    .isInstanceOf(EventNotFoundException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when vendor user does not exist")
        void throwsWhenVendorNotFound() {
            stubIsOrganizer(organizer);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(userRepository.findById(vendor.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.sendInquiry(eventId, createRequest(), organizer))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Vendor not found");
        }
    }

    // ─── closeInquiry() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("closeInquiry()")
    class CloseInquiry {

        @Test
        @DisplayName("Organizer closes a PENDING inquiry")
        void organizerClosesInquiry() {
            VendorInquiry inquiry = pendingInquiry();
            when(inquiryRepository.findById(inquiryId)).thenReturn(Optional.of(inquiry));
            when(inquiryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            VendorInquiryResponse response = service.closeInquiry(inquiryId, organizer);

            assertThat(response.getStatus()).isEqualTo(InquiryStatus.CLOSED);
        }

        @Test
        @DisplayName("Throws UnauthorizedException when vendor tries to close the inquiry")
        void throwsForVendor() {
            VendorInquiry inquiry = pendingInquiry();
            when(inquiryRepository.findById(inquiryId)).thenReturn(Optional.of(inquiry));

            assertThatThrownBy(() -> service.closeInquiry(inquiryId, vendor))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when inquiry does not exist")
        void throwsWhenNotFound() {
            when(inquiryRepository.findById(inquiryId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.closeInquiry(inquiryId, organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── listReceivedInquiries() ──────────────────────────────────────────────

    @Nested
    @DisplayName("listReceivedInquiries()")
    class ListReceived {

        @Test
        @DisplayName("Vendor receives all inquiries addressed to them")
        void vendorSeesReceivedInquiries() {
            VendorInquiry inquiry = pendingInquiry();
            when(inquiryRepository.findByVendorId(vendor.getId())).thenReturn(List.of(inquiry));

            List<VendorInquiryResponse> results = service.listReceivedInquiries(vendor);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getVendorId()).isEqualTo(vendor.getId());
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private VendorInquiry pendingInquiry() {
        return VendorInquiry.builder()
                .id(inquiryId)
                .event(event)
                .organizer(organizer)
                .vendor(vendor)
                .message("We need AV services for our event")
                .serviceType("AV")
                .status(InquiryStatus.PENDING)
                .build();
    }

    private CreateInquiryRequest createRequest() {
        CreateInquiryRequest req = new CreateInquiryRequest();
        req.setVendorId(vendor.getId());
        req.setMessage("We need AV services for our event");
        req.setServiceType("AV");
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
}
