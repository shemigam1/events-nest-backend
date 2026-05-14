package group.moniepoint.eventsnestserver.vendor.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.chat.model.Conversation;
import group.moniepoint.eventsnestserver.chat.model.ConversationParticipant;
import group.moniepoint.eventsnestserver.chat.model.ConversationType;
import group.moniepoint.eventsnestserver.chat.repository.ConversationRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VendorInquiryServiceImpl implements VendorInquiryService {

    private final VendorInquiryRepository inquiryRepository;
    private final EventRespository eventRepository;
    private final UserRepository userRepository;
    private final EventMembershipRepository membershipRepository;
    private final ConversationRepository conversationRepository;

    @Override
    @Transactional
    public VendorInquiryResponse sendInquiry(UUID eventId, CreateInquiryRequest request, User caller) {
        Events event = findEventOrThrow(eventId);
        assertOrganizer(eventId, caller);

        User vendor = userRepository.findById(request.getVendorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));

        if (inquiryRepository.existsByEventIdAndOrganizerIdAndVendorId(eventId, caller.getId(), vendor.getId())) {
            throw new IllegalStateException("An inquiry has already been sent to this vendor for this event");
        }

        // Reuse existing DM or create a new one
        Conversation conversation = conversationRepository
                .findDirectBetween(ConversationType.DIRECT, caller.getId(), vendor.getId())
                .orElseGet(() -> createDirectConversation(caller, vendor));

        VendorInquiry inquiry = VendorInquiry.builder()
                .event(event)
                .organizer(caller)
                .vendor(vendor)
                .message(request.getMessage())
                .serviceType(request.getServiceType())
                .conversation(conversation)
                .status(InquiryStatus.PENDING)
                .build();

        inquiryRepository.save(inquiry);
        log.info("Vendor inquiry {} sent to {} for event {}", inquiry.getId(), vendor.getId(), eventId);
        return VendorInquiryResponse.from(inquiry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorInquiryResponse> listSentInquiries(UUID eventId, User caller) {
        assertOrganizer(eventId, caller);
        return inquiryRepository.findByEventIdAndOrganizerId(eventId, caller.getId()).stream()
                .map(VendorInquiryResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorInquiryResponse> listReceivedInquiries(User caller) {
        return inquiryRepository.findByVendorId(caller.getId()).stream()
                .map(VendorInquiryResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public VendorInquiryResponse closeInquiry(UUID inquiryId, User caller) {
        VendorInquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found"));

        if (!inquiry.getOrganizer().getId().equals(caller.getId())) {
            throw new UnauthorizedException("only the organizer can close an inquiry");
        }

        inquiry.setStatus(InquiryStatus.CLOSED);
        inquiryRepository.save(inquiry);
        return VendorInquiryResponse.from(inquiry);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private Events findEventOrThrow(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }

    private void assertOrganizer(UUID eventId, User user) {
        boolean ok = membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER);
        if (!ok) throw new UnauthorizedException("only the event organizer can perform this action");
    }

    private Conversation createDirectConversation(User organizer, User vendor) {
        List<ConversationParticipant> participants = new ArrayList<>();
        Conversation conversation = Conversation.builder()
                .type(ConversationType.DIRECT)
                .createdBy(organizer)
                .participants(participants)
                .build();
        conversationRepository.save(conversation);

        participants.add(ConversationParticipant.builder().conversation(conversation).user(organizer).build());
        participants.add(ConversationParticipant.builder().conversation(conversation).user(vendor).build());
        return conversationRepository.save(conversation);
    }
}
