package group.moniepoint.eventsnestserver.seed;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.chat.model.Conversation;
import group.moniepoint.eventsnestserver.chat.model.ConversationParticipant;
import group.moniepoint.eventsnestserver.chat.model.ConversationType;
import group.moniepoint.eventsnestserver.chat.model.Message;
import group.moniepoint.eventsnestserver.chat.repository.ConversationParticipantRepository;
import group.moniepoint.eventsnestserver.chat.repository.ConversationRepository;
import group.moniepoint.eventsnestserver.chat.repository.MessageRepository;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplicationStatus;
import group.moniepoint.eventsnestserver.vendor.repository.VendorApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class VendorSeeder {

    private final VendorApplicationRepository        vendorApplicationRepository;
    private final ConversationRepository             conversationRepository;
    private final ConversationParticipantRepository  conversationParticipantRepository;
    private final MessageRepository                  messageRepository;
    private final UserRepository                     userRepository;

    // ─── seed data ────────────────────────────────────────────────────────────

    record VendorSeed(
            int organiserIndex, int eventIndex,
            int applicantIndex,
            String serviceType, String description,
            BigDecimal proposedAmount,
            VendorApplicationStatus status) {}

    static final List<VendorSeed> VENDOR_SEEDS = List.of(

            // ACCEPTED
            new VendorSeed(0, 0, 3, "Catering",
                    "Full event catering — canapes, small chops, cocktail bar for 500 guests.",
                    new BigDecimal("450000"), VendorApplicationStatus.ACCEPTED),
            new VendorSeed(1, 0, 4, "Security",
                    "Crowd management and access control — 20 trained security personnel.",
                    new BigDecimal("200000"), VendorApplicationStatus.ACCEPTED),
            new VendorSeed(2, 0, 6, "MC / Host",
                    "Professional MC and stage host for the full conference day.",
                    new BigDecimal("150000"), VendorApplicationStatus.ACCEPTED),
            new VendorSeed(3, 0, 9, "Decoration",
                    "Full venue dressing — florals, branded signage, stage backdrop.",
                    new BigDecimal("180000"), VendorApplicationStatus.ACCEPTED),
            new VendorSeed(4, 0, 7, "First Aid / Medical",
                    "On-site medical team — 2 paramedics, first aid station, emergency response.",
                    new BigDecimal("120000"), VendorApplicationStatus.ACCEPTED),

            // PENDING
            new VendorSeed(8, 0, 1, "Live Music",
                    "Live band performance during cocktail reception — 3-hour set.",
                    new BigDecimal("300000"), VendorApplicationStatus.PENDING),
            new VendorSeed(9, 0, 2, "Audio Visual",
                    "Full runway AV: PA system, LED wall, lighting rig, and livestream.",
                    new BigDecimal("850000"), VendorApplicationStatus.PENDING),
            new VendorSeed(5, 0, 0, "Photography",
                    "Event photography — candid shots, speaker portraits, gallery delivered in 48h.",
                    new BigDecimal("95000"), VendorApplicationStatus.PENDING),
            new VendorSeed(6, 0, 8, "Logistics",
                    "Venue setup, seating arrangement, and post-event breakdown crew.",
                    new BigDecimal("110000"), VendorApplicationStatus.PENDING),
            new VendorSeed(2, 1, 5, "Printing & Branding",
                    "Conference programmes, banners, roll-ups, branded lanyards for 300 attendees.",
                    new BigDecimal("75000"), VendorApplicationStatus.PENDING),

            // REJECTED
            new VendorSeed(0, 1, 9, "Decoration",
                    "Premium décor package — budget far exceeds the event's vendor allocation.",
                    new BigDecimal("900000"), VendorApplicationStatus.REJECTED),
            new VendorSeed(1, 1, 4, "Security",
                    "Security proposal — position already filled by another vendor.",
                    new BigDecimal("180000"), VendorApplicationStatus.REJECTED),
            new VendorSeed(3, 1, 0, "Tech Support",
                    "IT support desk — not applicable for a wellness/fitness event.",
                    new BigDecimal("60000"), VendorApplicationStatus.REJECTED),
            new VendorSeed(8, 1, 2, "Logistics",
                    "Logistics proposal — venue provides its own in-house setup team.",
                    new BigDecimal("95000"), VendorApplicationStatus.REJECTED),
            new VendorSeed(7, 1, 6, "MC / Host",
                    "MC services — committee decided on an internal moderator for the symposium.",
                    new BigDecimal("80000"), VendorApplicationStatus.REJECTED)
    );

    private static final String[] ORGANIZER_MESSAGES = {
            "Hi, interested in your services for our event.",
            "Can you share your availability and timeline?",
            "What's your experience with events of this size?",
            "Let's discuss the details and finalize the contract.",
            "Excellent! We're excited to work with you."
    };

    private static final String[] VENDOR_MESSAGES = {
            "Thanks for reaching out! Happy to help with your event.",
            "We're available during that period. Here are our rates.",
            "We've handled 50+ similar events. Check our portfolio.",
            "Agreed. When can we meet to sign the contract?",
            "Perfect! Looking forward to delivering quality service."
    };

    // ─── seeding ─────────────────────────────────────────────────────────────

    List<VendorApplication> seedApplications(List<User> users, List<List<Events>> eventsByUser) {
        User admin = userRepository.findByEmail("admin@eventsnest.com").orElse(null);
        List<VendorApplication> created = new ArrayList<>();

        for (VendorSeed vs : VENDOR_SEEDS) {
            Events event     = eventsByUser.get(vs.organiserIndex()).get(vs.eventIndex());
            User   applicant = users.get(vs.applicantIndex());

            if (event.getCreatedBy().getId().equals(applicant.getId())) {
                log.warn("Skipping vendor seed — applicant is the organiser: {}", event.getTitle());
                continue;
            }

            if (vendorApplicationRepository.existsByEventIdAndApplicantIdAndServiceType(
                    event.getId(), applicant.getId(), vs.serviceType())) {
                log.debug("Vendor application already exists, skipping: {} / {}", event.getTitle(), vs.serviceType());
                continue;
            }

            boolean reviewed = vs.status() != VendorApplicationStatus.PENDING;
            VendorApplication app = vendorApplicationRepository.save(VendorApplication.builder()
                    .event(event).applicant(applicant)
                    .serviceType(vs.serviceType()).description(vs.description())
                    .proposedAmount(vs.proposedAmount()).status(vs.status())
                    .reviewedBy(reviewed ? admin : null)
                    .reviewedAt(reviewed ? LocalDateTime.now() : null)
                    .build());
            created.add(app);

            log.debug("Seeded vendor application: [{}] {} → {} ({})",
                    vs.status(), applicant.getEmail(), event.getTitle(), vs.serviceType());
        }
        return created;
    }

    Map<VendorApplication, Conversation> seedConversations(
            List<User> users, List<List<Events>> eventsByUser, List<VendorApplication> vendorApps) {

        Map<VendorApplication, Conversation> conversations = new HashMap<>();

        for (VendorApplication app : vendorApps) {
            User organizer = app.getEvent().getCreatedBy();
            User vendor    = app.getApplicant();

            Conversation conv = conversationRepository.save(Conversation.builder()
                    .title(vendor.getFirstName() + " " + vendor.getLastName() + " - " + app.getServiceType())
                    .event(app.getEvent())
                    .type(ConversationType.DIRECT)
                    .createdBy(organizer)
                    .build());

            conversationParticipantRepository.save(ConversationParticipant.builder()
                    .conversation(conv).user(organizer).build());
            conversationParticipantRepository.save(ConversationParticipant.builder()
                    .conversation(conv).user(vendor).build());

            conversations.put(app, conv);
            log.debug("Seeded conversation: {} ↔ {} for {}", organizer.getEmail(), vendor.getEmail(), app.getServiceType());
        }
        return conversations;
    }

    void seedMessages(Map<VendorApplication, Conversation> conversations) {
        int messageIndex = 0;
        for (Map.Entry<VendorApplication, Conversation> entry : conversations.entrySet()) {
            Conversation conv     = entry.getValue();
            VendorApplication app = entry.getKey();
            User organizer        = app.getEvent().getCreatedBy();
            User vendor           = app.getApplicant();

            int msgCount = 5 + (messageIndex % 3);
            for (int i = 0; i < msgCount; i++) {
                User   sender  = (i % 2 == 0) ? organizer : vendor;
                String content = (i % 2 == 0)
                        ? ORGANIZER_MESSAGES[i % ORGANIZER_MESSAGES.length]
                        : VENDOR_MESSAGES[i % VENDOR_MESSAGES.length];

                messageRepository.save(Message.builder()
                        .conversation(conv).sender(sender).content(content)
                        .build());
            }
            messageIndex++;
            log.debug("Seeded {} messages in conversation with {}", msgCount, vendor.getEmail());
        }
    }

    List<VendorApplication> reloadApps() {
        return vendorApplicationRepository.findAll();
    }

    Map<VendorApplication, group.moniepoint.eventsnestserver.chat.model.Conversation> reloadConversations(
            List<VendorApplication> apps) {
        Map<VendorApplication, group.moniepoint.eventsnestserver.chat.model.Conversation> map = new HashMap<>();
        for (VendorApplication app : apps) {
            String title = app.getApplicant().getFirstName() + " "
                    + app.getApplicant().getLastName() + " - " + app.getServiceType();
            conversationRepository.findByTitleAndEventId(title, app.getEvent().getId())
                    .ifPresent(conv -> map.put(app, conv));
        }
        return map;
    }
}
