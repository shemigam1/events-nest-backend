package group.moniepoint.eventsnestserver.seed;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.model.VendorVerificationStatus;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.events.models.*;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import group.moniepoint.eventsnestserver.guestlist.model.Guest;
import group.moniepoint.eventsnestserver.guestlist.model.RsvpStatus;
import group.moniepoint.eventsnestserver.guestlist.repository.GuestRepository;
import group.moniepoint.eventsnestserver.programme.model.ProgrammeItem;
import group.moniepoint.eventsnestserver.programme.repository.ProgrammeItemRepository;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplicationStatus;
import group.moniepoint.eventsnestserver.vendor.repository.VendorApplicationRepository;
import group.moniepoint.eventsnestserver.chat.model.Conversation;
import group.moniepoint.eventsnestserver.chat.model.ConversationParticipant;
import group.moniepoint.eventsnestserver.chat.model.ConversationType;
import group.moniepoint.eventsnestserver.chat.model.Message;
import group.moniepoint.eventsnestserver.chat.repository.ConversationRepository;
import group.moniepoint.eventsnestserver.chat.repository.ConversationParticipantRepository;
import group.moniepoint.eventsnestserver.chat.repository.MessageRepository;
import group.moniepoint.eventsnestserver.contracts.model.VendorContract;
import group.moniepoint.eventsnestserver.contracts.model.ContractStatus;
import group.moniepoint.eventsnestserver.contracts.model.EscrowAccount;
import group.moniepoint.eventsnestserver.contracts.model.EscrowMilestone;
import group.moniepoint.eventsnestserver.contracts.model.EscrowStatus;
import group.moniepoint.eventsnestserver.contracts.model.MilestoneStatus;
import group.moniepoint.eventsnestserver.contracts.repository.VendorContractRepository;
import group.moniepoint.eventsnestserver.contracts.repository.EscrowAccountRepository;
import group.moniepoint.eventsnestserver.contracts.repository.EscrowMilestoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Seeds realistic demo data for local development and demos.
 * Only active on the "local" Spring profile — never runs in production.
 *
 * What gets created:
 *   - 30+ users across themed verticals (extensible for more)
 *   - 150+ events (5 per user: mix of PUBLISHED, PENDING_APPROVAL, DRAFT, CANCELLED)
 *   - Ticket tiers (General + VIP) per event; free events get a single ₦0 tier
 *   - Organiser memberships for every event
 *   - Vendor applications in all three states (ACCEPTED / PENDING / REJECTED)
 *   - Conversations between organizers and vendors (DIRECT and GROUP)
 *   - Messages in conversations for realistic chat history
 *   - Vendor Contracts from ACCEPTED vendor applications
 *   - Escrow Accounts with Milestones for contract-based payments
 *
 * Idempotent — skips everything if the sentinel email already exists.
 * Tier backfill runs on repeat starts to repair any tierless events.
 */
@Slf4j
@Component
@Profile("local")
@Order(10)
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final UserRepository              userRepository;
    private final EventRespository            eventRepository;
    private final EventConfigRepository       eventConfigRepository;
    private final EventMembershipRepository   membershipRepository;
    private final TicketTierRepository        tierRepository;
    private final VendorApplicationRepository vendorApplicationRepository;
    private final ProgrammeItemRepository     programmeItemRepository;
    private final GuestRepository             guestRepository;
    private final ConversationRepository      conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;
    private final MessageRepository           messageRepository;
    private final VendorContractRepository    vendorContractRepository;
    private final EscrowAccountRepository     escrowAccountRepository;
    private final EscrowMilestoneRepository   escrowMilestoneRepository;
    private final PasswordEncoder             passwordEncoder;

    private static final String SEED_CHECK_EMAIL = "akin@devmail.com";
    private static final String DEFAULT_PASSWORD  = "Password1!";

    // ─── user seed data ───────────────────────────────────────────────────────

    private static final String[][] USERS = {
            {"Akin",      "Ogundimu", "akin@devmail.com"},
            {"Chioma",    "Eze",      "chioma@devmail.com"},
            {"Emeka",     "Obi",      "emeka@devmail.com"},
            {"Funke",     "Adeyemi",  "funke@devmail.com"},
            {"Gbenga",    "Lawal",    "gbenga@devmail.com"},
            {"Halima",    "Musa",     "halima@devmail.com"},
            {"Ifeanyi",   "Nwosu",    "ifeanyi@devmail.com"},
            {"Jumoke",    "Bello",    "jumoke@devmail.com"},
            {"Kola",      "Adebayo",  "kola@devmail.com"},
            {"Lara",      "Williams", "lara@devmail.com"},

            // Additional users (11-20)
            {"Musa",      "Ibrahim",  "musa@devmail.com"},
            {"Ngozi",     "Okonkwo",  "ngozi@devmail.com"},
            {"Oluwatoyin","Adeyoke",  "oluwatoyin@devmail.com"},
            {"Precious",  "Nwankwo",  "precious@devmail.com"},
            {"Rasheed",   "Yusuf",    "rasheed@devmail.com"},
            {"Seun",      "Akanji",   "seun@devmail.com"},
            {"Tunde",     "Okafor",   "tunde@devmail.com"},
            {"Uche",      "Anyanwu",  "uche@devmail.com"},
            {"Vicky",     "Osei",     "vicky@devmail.com"},
            {"Wuraola",   "Lawal",    "wuraola@devmail.com"},

            // Additional users (21-30)
            {"Xolani",    "Zuma",     "xolani@devmail.com"},
            {"Yetunde",   "Ajayi",    "yetunde@devmail.com"},
            {"Zainab",    "Hassan",   "zainab@devmail.com"},
            {"Amara",     "Okafor",   "amara@devmail.com"},
            {"Bolaji",    "Oni",      "bolaji@devmail.com"},
            {"Chidinma",  "Obi",      "chidinma@devmail.com"},
            {"Daniel",    "Ibekwe",   "daniel@devmail.com"},
            {"Ebube",     "Eze",      "ebube@devmail.com"},
            {"Folake",    "Adekunle", "folake@devmail.com"},
            {"Gbemi",     "Kolade",   "gbemi@devmail.com"},

            // Additional users (31+)
            {"Habiba",    "Musa",     "habiba@devmail.com"},
            {"Ibrahim",   "Ahmed",    "ibrahim@devmail.com"},
    };

    // ─── platform-verified vendor profiles ───────────────────────────────────
    //
    // Keyed by USERS index.  Only these users get vendorVerified=true so the
    // marketplace endpoint returns actual data straight away.
    // Chosen to match the ACCEPTED vendor applications below (applicantIndex).

    private record VerifiedVendorProfile(int userIndex, String serviceType, String bio) {}

    private static final List<VerifiedVendorProfile> VERIFIED_VENDORS = List.of(
            new VerifiedVendorProfile(3, "Catering",
                    "Full-service event caterer specialising in high-volume corporate and social events across Lagos."),
            new VerifiedVendorProfile(4, "Security",
                    "Licensed crowd management and VIP protection company with 50+ trained operatives."),
            new VerifiedVendorProfile(6, "MC / Host",
                    "Professional bilingual MC and stage host with 8 years of conference and gala experience."),
            new VerifiedVendorProfile(9, "Decoration",
                    "Award-winning event decorator — florals, bespoke installations, and branded environments."),
            new VerifiedVendorProfile(7, "First Aid / Medical",
                    "Certified paramedic team providing on-site medical cover for events of all sizes.")
    );

    // ─── venue pool ───────────────────────────────────────────────────────────

    private static final String[] VENUES = {
            "Eko Convention Centre, Lagos",
            "Landmark Centre, Lagos",
            "MUSON Centre, Lagos",
            "Radisson Blu Hotel, Lagos",
            "Transcorp Hilton, Abuja",
            "Oriental Hotel, Lagos",
            "NAF Conference Centre, Abuja",
            "Four Points by Sheraton, Lagos",
            "Federal Palace Hotel, Lagos",
            "Civic Centre, Lagos",
    };

    // ─── tier seed data ───────────────────────────────────────────────────────

    private record TierSeed(String name, BigDecimal price, String rowPrefix, int rowCount, int seatsPerRow) {}

    private static final List<List<TierSeed>> TIERS_PER_THEME = List.of(
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("25000"), "V", 5, 5)),  // Akin - Tech
            List.of(new TierSeed("General", new BigDecimal("8000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("30000"), "V", 5, 5)),  // Chioma - Music
            List.of(new TierSeed("General", new BigDecimal("10000"), "G", 15, 10), new TierSeed("VIP", new BigDecimal("50000"), "V", 4, 5)),  // Emeka - Business
            List.of(new TierSeed("General", new BigDecimal("3000"),  "G", 30, 10), new TierSeed("VIP", new BigDecimal("15000"), "V", 6, 5)),  // Funke - Food
            List.of(new TierSeed("General", new BigDecimal("2000"),  "G", 50, 10), new TierSeed("VIP", new BigDecimal("10000"), "V", 10, 5)), // Gbenga - Sports
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5)),  // Halima - Education
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5)),  // Ifeanyi - Entertainment
            List.of(new TierSeed("General", new BigDecimal("3000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("15000"), "V", 5, 5)),  // Jumoke - Health
            List.of(new TierSeed("General", new BigDecimal("10000"), "G", 10, 10), new TierSeed("VIP", new BigDecimal("50000"), "V", 4, 5)),  // Kola - Real Estate
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5)),  // Lara - Fashion
            // Users 11-20
            List.of(new TierSeed("General", new BigDecimal("4000"),  "G", 25, 10), new TierSeed("VIP", new BigDecimal("18000"), "V", 5, 5)),  // Musa - Marketing
            List.of(new TierSeed("General", new BigDecimal("6000"),  "G", 18, 10), new TierSeed("VIP", new BigDecimal("28000"), "V", 5, 5)),  // Ngozi - Media
            List.of(new TierSeed("General", new BigDecimal("3500"),  "G", 30, 10), new TierSeed("VIP", new BigDecimal("16000"), "V", 6, 5)),  // Oluwatoyin - Wellness
            List.of(new TierSeed("General", new BigDecimal("5500"),  "G", 22, 10), new TierSeed("VIP", new BigDecimal("22000"), "V", 5, 5)),  // Precious - Arts
            List.of(new TierSeed("General", new BigDecimal("7000"),  "G", 16, 10), new TierSeed("VIP", new BigDecimal("35000"), "V", 4, 5)),  // Rasheed - Logistics
            List.of(new TierSeed("General", new BigDecimal("4500"),  "G", 24, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5)),  // Seun - Tech Summit
            List.of(new TierSeed("General", new BigDecimal("6500"),  "G", 19, 10), new TierSeed("VIP", new BigDecimal("32000"), "V", 5, 5)),  // Tunde - Finance
            List.of(new TierSeed("General", new BigDecimal("3200"),  "G", 35, 10), new TierSeed("VIP", new BigDecimal("14000"), "V", 7, 5)),  // Uche - Community
            List.of(new TierSeed("General", new BigDecimal("5200"),  "G", 21, 10), new TierSeed("VIP", new BigDecimal("19000"), "V", 5, 5)),  // Vicky - Culture
            List.of(new TierSeed("General", new BigDecimal("8500"),  "G", 17, 10), new TierSeed("VIP", new BigDecimal("40000"), "V", 4, 5)),  // Wuraola - Innovation
            // Users 21-30
            List.of(new TierSeed("General", new BigDecimal("4700"),  "G", 23, 10), new TierSeed("VIP", new BigDecimal("21000"), "V", 5, 5)),  // Xolani - Summit
            List.of(new TierSeed("General", new BigDecimal("5800"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("26000"), "V", 5, 5)),  // Yetunde - Expo
            List.of(new TierSeed("General", new BigDecimal("6200"),  "G", 18, 10), new TierSeed("VIP", new BigDecimal("29000"), "V", 5, 5)),  // Zainab - Conference
            List.of(new TierSeed("General", new BigDecimal("3800"),  "G", 32, 10), new TierSeed("VIP", new BigDecimal("17000"), "V", 6, 5)),  // Amara - Workshop
            List.of(new TierSeed("General", new BigDecimal("7200"),  "G", 15, 10), new TierSeed("VIP", new BigDecimal("38000"), "V", 4, 5)),  // Bolaji - Forum
            List.of(new TierSeed("General", new BigDecimal("4300"),  "G", 26, 10), new TierSeed("VIP", new BigDecimal("19500"), "V", 5, 5)),  // Chidinma - Festival
            List.of(new TierSeed("General", new BigDecimal("6800"),  "G", 19, 10), new TierSeed("VIP", new BigDecimal("31000"), "V", 5, 5)),  // Daniel - Retreat
            List.of(new TierSeed("General", new BigDecimal("5400"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("24000"), "V", 5, 5)),  // Ebube - Gala
            List.of(new TierSeed("General", new BigDecimal("7500"),  "G", 14, 10), new TierSeed("VIP", new BigDecimal("42000"), "V", 4, 5)),  // Folake - Symposium
            List.of(new TierSeed("General", new BigDecimal("4100"),  "G", 28, 10), new TierSeed("VIP", new BigDecimal("18500"), "V", 6, 5)),  // Gbemi - Carnival
            // Users 31-32
            List.of(new TierSeed("General", new BigDecimal("6900"),  "G", 17, 10), new TierSeed("VIP", new BigDecimal("33000"), "V", 5, 5)),  // Habiba - Awards
            List.of(new TierSeed("General", new BigDecimal("5600"),  "G", 21, 10), new TierSeed("VIP", new BigDecimal("25500"), "V", 5, 5))   // Ibrahim - Showcase
    );

    // ─── event seed data (5 per user = 50 total) ─────────────────────────────

    private record EventSeed(
            String title, String description,
            int startDaysFromNow, int durationHours,
            EventStatus status, boolean free,
            EventVisibility visibility, boolean guestListEnabled) {}

    /** Convenience factory — public event, no guest list (most common). */
    private static EventSeed pub(String title, String desc, int days, int hrs, EventStatus status, boolean free) {
        return new EventSeed(title, desc, days, hrs, status, free, EventVisibility.PUBLIC, false);
    }

    /** Private event with guest list enabled. */
    private static EventSeed priv(String title, String desc, int days, int hrs, EventStatus status, boolean free) {
        return new EventSeed(title, desc, days, hrs, status, free, EventVisibility.PRIVATE, true);
    }

    private static final List<List<EventSeed>> EVENTS_PER_USER = List.of(

            // Akin — Tech  (index 0)
            List.of(
                    pub( "TechFest Lagos 2026",            "The biggest tech festival in West Africa.",                45,  8, EventStatus.PUBLISHED,        false),
                    pub( "AI & Machine Learning Summit",   "Exploring cutting-edge AI applications.",                  20,  6, EventStatus.PUBLISHED,        false),
                    priv("Blockchain Developers Bootcamp", "Hands-on blockchain development training — invite-only.",  60,  8, EventStatus.PUBLISHED,        false),
                    pub( "Startup Pitch Night",            "Free — early-stage founders pitch to investors.",          30,  4, EventStatus.DRAFT,            true),
                    pub( "Digital Innovation Conference",  "Past conference on digital transformation.",              -20,  6, EventStatus.PUBLISHED,        false)
            ),

            // Chioma — Music & Arts  (index 1)
            List.of(
                    pub( "Afrobeats Night Out",            "Live Afrobeats performances by top artists.",              15,  5, EventStatus.PUBLISHED,        false),
                    pub( "Lagos Jazz Festival",            "Three days of world-class jazz.",                          90,  6, EventStatus.PUBLISHED,        false),
                    pub( "Open Mic Wednesday",             "Free — discover emerging spoken word and music talent.",    7,  3, EventStatus.DRAFT,            true),
                    priv("Gospel Concert 2026",            "An uplifting evening of gospel music — members only.",     50,  4, EventStatus.PUBLISHED,        false),
                    pub( "Highlife Revival Night",         "Celebrating the golden era of highlife music.",           -30,  5, EventStatus.PUBLISHED,        false)
            ),

            // Emeka — Business  (index 2)
            List.of(
                    pub( "Entrepreneurs Summit 2026",      "Connecting founders, mentors and investors.",              35,  8, EventStatus.PUBLISHED,        false),
                    priv("SME Growth Conference",          "Practical strategies for small business growth — by invitation.", 55, 6, EventStatus.PUBLISHED, false),
                    pub( "Investment & Finance Forum",     "Understanding the Nigerian capital markets.",               40,  5, EventStatus.PENDING_APPROVAL, false),
                    pub( "Business Networking Brunch",     "Free — curated networking for professionals.",             10,  3, EventStatus.DRAFT,            true),
                    pub( "Export Trade Fair",              "Cancelled due to venue unavailability.",                   25,  8, EventStatus.CANCELLED,        false)
            ),

            // Funke — Food & Lifestyle  (index 3)
            List.of(
                    pub( "Lagos Food & Wine Festival",     "Celebrating Nigeria's rich culinary culture.",             28,  8, EventStatus.PUBLISHED,        false),
                    pub( "Healthy Living Expo",            "Wellness, nutrition and fitness under one roof.",          70,  6, EventStatus.PUBLISHED,        false),
                    pub( "Street Food Carnival",           "Free — a tour of Lagos's best street food vendors.",       14,  5, EventStatus.DRAFT,            true),
                    priv("Culinary Arts Masterclass",      "Learn from award-winning chefs — intimate class, limited seats.", 42, 4, EventStatus.PUBLISHED,        false),
                    pub( "Farm-to-Table Dinner Experience","An intimate dinner using locally sourced produce.",        -15,  4, EventStatus.PUBLISHED,        false)
            ),

            // Gbenga — Sports  (index 4)
            List.of(
                    pub( "Lagos Marathon 2026",            "Annual marathon through the heart of Lagos.",             100,  8, EventStatus.PUBLISHED,        false),
                    pub( "Inter-Company Football League",  "Corporate 5-a-side football tournament.",                  22,  6, EventStatus.PUBLISHED,        false),
                    pub( "Basketball Invitational",        "Top university basketball teams compete.",                  33,  6, EventStatus.DRAFT,            false),
                    priv("Swimming Championship",          "National age-group swimming competition — participants and families only.", 48, 8, EventStatus.PUBLISHED,        false),
                    pub( "Fitness & Wellness Weekend",     "Free — past weekend fitness retreat.",                    -10,  8, EventStatus.PUBLISHED,        true)
            ),

            // Halima — Education  (index 5)
            List.of(
                    pub( "Women in STEM Conference",       "Inspiring the next generation of female engineers.",       38,  6, EventStatus.PUBLISHED,        false),
                    pub( "Youth Leadership Summit",        "Empowering young leaders across Nigeria.",                 65,  8, EventStatus.PUBLISHED,        false),
                    pub( "Creative Writing Workshop",      "Free — develop your voice through guided writing.",        12,  4, EventStatus.DRAFT,            true),
                    pub( "Science & Technology Fair",      "Showcasing student innovations and inventions.",           52,  8, EventStatus.PENDING_APPROVAL, false),
                    pub( "Graduate Career Expo",           "Free — cancelled, rescheduled to next quarter.",           18,  6, EventStatus.CANCELLED,        true)
            ),

            // Ifeanyi — Entertainment  (index 6)
            List.of(
                    pub( "Comedy Night Live",              "Nigeria's funniest comedians on one stage.",                8,  3, EventStatus.PUBLISHED,        false),
                    priv("Nollywood Film Premiere",        "Exclusive premiere of an anticipated feature film — press and VIP guests only.", 44, 3, EventStatus.PUBLISHED, false),
                    pub( "Stand-Up Showcase",              "Free — open showcase for emerging stand-up comedians.",    19,  3, EventStatus.DRAFT,            true),
                    pub( "Fashion & Style Show",           "Runway show featuring Nigerian designers.",                 36,  4, EventStatus.PENDING_APPROVAL, false),
                    pub( "Cultural Heritage Night",        "Free — celebrating Nigeria's diverse cultural traditions.",-25,  5, EventStatus.PUBLISHED,       true)
            ),

            // Jumoke — Health  (index 7)
            List.of(
                    pub( "Mental Health Awareness Day",    "Free — breaking stigma and promoting mental wellness.",    26,  6, EventStatus.PUBLISHED,        true),
                    pub( "Healthcare Innovation Summit",   "The future of healthcare delivery in Africa.",             80,  8, EventStatus.PUBLISHED,        false),
                    priv("Yoga & Mindfulness Retreat",     "Free — a full-day retreat for registered participants only.", 16, 8, EventStatus.PUBLISHED,        true),
                    pub( "Medical Research Symposium",     "Presenting breakthroughs in Nigerian healthcare.",          58,  6, EventStatus.PENDING_APPROVAL, false),
                    pub( "Community Health Fair",          "Free — screenings and health education for all.",           -5,  6, EventStatus.PUBLISHED,       true)
            ),

            // Kola — Real Estate  (index 8)
            List.of(
                    pub( "Lagos Property Expo",            "The premier real estate showcase in West Africa.",          32,  8, EventStatus.PUBLISHED,        false),
                    priv("Real Estate Investment Forum",   "Strategies for building a profitable property portfolio — accredited investors only.", 68, 6, EventStatus.PUBLISHED, false),
                    pub( "Architecture & Design Showcase", "Free — featuring Nigeria's most innovative architects.",    23,  5, EventStatus.DRAFT,            true),
                    pub( "Smart Cities Conference",        "Urban planning and technology for future cities.",          46,  6, EventStatus.PENDING_APPROVAL, false),
                    pub( "Housing Finance Summit",         "Cancelled — speaker scheduling conflict.",                  29,  5, EventStatus.CANCELLED,        false)
            ),

            // Lara — Fashion  (index 9)
            List.of(
                    pub( "Lagos Fashion Week",             "The continent's most prestigious fashion event.",           75,  8, EventStatus.PUBLISHED,        false),
                    pub( "Designers Showcase 2026",        "Up-and-coming Nigerian designers take the spotlight.",      18,  5, EventStatus.PUBLISHED,        false),
                    priv("Fabric & Textile Market",        "Free — premium fabrics, trade buyers and designers only.",   9,  6, EventStatus.PUBLISHED,        true),
                    pub( "Beauty & Cosmetics Expo",        "Celebrating African beauty brands and innovations.",         54,  6, EventStatus.PENDING_APPROVAL, false),
                    pub( "Vintage Clothing Fair",          "A curated showcase of pre-loved fashion pieces.",           -40,  5, EventStatus.PUBLISHED,       false)
            ),

            // Musa — Marketing & Growth  (index 10)
            List.of(
                    pub( "Digital Marketing Summit 2026",  "Mastering modern digital strategies and SEO.",              42,  7, EventStatus.PUBLISHED,        false),
                    pub( "Growth Hacking Workshop",        "Learn proven tactics to scale your startup.",               16,  5, EventStatus.PUBLISHED,        false),
                    priv("Brand Strategy Intensive",       "Hands-on workshop for brand positioning — limited seats.",   28,  8, EventStatus.PUBLISHED,        false),
                    pub( "Social Media Masterclass",       "Free — maximize your social media presence.",               11,  4, EventStatus.DRAFT,            true),
                    pub( "Marketing Analytics Forum",      "Data-driven decisions in modern marketing.",               -12,  5, EventStatus.PUBLISHED,        false)
            ),

            // Ngozi — Media & Broadcasting  (index 11)
            List.of(
                    pub( "Media Production Conference",    "Latest trends in broadcast and digital media.",             37,  8, EventStatus.PUBLISHED,        false),
                    pub( "Podcast & Audio Summit",         "The rise of audio content in Nigeria.",                     22,  6, EventStatus.PUBLISHED,        false),
                    priv("Content Creator Roundtable",     "Free — exclusive discussion with top creators.",            13,  4, EventStatus.PUBLISHED,        true),
                    pub( "Television & Film Festival",     "Celebrating Nigerian storytelling excellence.",             67,  8, EventStatus.PENDING_APPROVAL, false),
                    pub( "Journalism Ethics Forum",        "Ethical reporting in the digital age.",                    -18,  5, EventStatus.PUBLISHED,        false)
            ),

            // Oluwatoyin — Wellness & Fitness  (index 12)
            List.of(
                    pub( "Holistic Health Expo 2026",      "Mind, body, and spirit wellness integration.",              32,  7, EventStatus.PUBLISHED,        false),
                    pub( "Fitness Challenge Series",       "12-week community fitness transformation.",                25,  6, EventStatus.PUBLISHED,        false),
                    priv("Personal Training Certification","Become a certified fitness trainer — professional track.",    41,  8, EventStatus.PUBLISHED,        false),
                    pub( "Nutrition & Diet Workshop",      "Free — science-backed nutrition for optimal health.",        8,  4, EventStatus.DRAFT,            true),
                    pub( "Wellness Retreat",               "A weekend of rejuvenation and self-care.",                  -22,  8, EventStatus.PUBLISHED,        false)
            ),

            // Precious — Arts & Culture  (index 13)
            List.of(
                    pub( "Contemporary Art Exhibition",    "Showcasing emerging Nigerian visual artists.",              31,  7, EventStatus.PUBLISHED,        false),
                    pub( "Sculpture & Installation Art",   "A deep dive into 3D artistic expression.",                  17,  5, EventStatus.PUBLISHED,        false),
                    priv("Art Collectors' Dinner",         "Free — curated evening for art enthusiasts.",               19,  5, EventStatus.PUBLISHED,        true),
                    pub( "Performing Arts Showcase",       "Dance, theatre, and live performance festival.",            49,  8, EventStatus.PENDING_APPROVAL, false),
                    pub( "Traditional Crafts Market",      "Free — celebrating Nigerian artisanal traditions.",        -35,  6, EventStatus.PUBLISHED,       true)
            ),

            // Rasheed — Logistics & Supply Chain  (index 14)
            List.of(
                    pub( "Supply Chain Optimization Forum","Best practices in logistics and procurement.",              39,  7, EventStatus.PUBLISHED,        false),
                    pub( "Warehouse Management Summit",    "Modern warehousing and inventory systems.",                 21,  6, EventStatus.PUBLISHED,        false),
                    priv("Logistics Technology Workshop", "AI and automation in supply chains — professionals only.",   33,  8, EventStatus.PUBLISHED,        false),
                    pub( "Last-Mile Delivery Conference",  "Free — the future of delivery networks.",                    14,  4, EventStatus.DRAFT,            true),
                    pub( "Trade & Customs Forum",          "International shipping and regulatory compliance.",         -28,  5, EventStatus.PUBLISHED,        false)
            ),

            // Seun — Tech Summit  (index 15)
            List.of(
                    pub( "Web3 & Blockchain Summit",       "The future of decentralized applications.",                 44,  8, EventStatus.PUBLISHED,        false),
                    pub( "Cloud Architecture Masterclass", "Building scalable cloud solutions.",                        20,  6, EventStatus.PUBLISHED,        false),
                    priv("CTO Roundtable & Networking",    "Exclusive for tech leaders and decision makers.",           56,  4, EventStatus.PUBLISHED,        false),
                    pub( "Cybersecurity Workshop",         "Free — protect your digital assets.",                        12,  5, EventStatus.DRAFT,            true),
                    pub( "Startup Tech Stack Seminar",     "Choosing the right tools for your startup.",               -15,  5, EventStatus.PUBLISHED,        false)
            ),

            // Tunde — Finance & Investment  (index 16)
            List.of(
                    pub( "Investment Summit 2026",         "Wealth building and portfolio management.",                 38,  8, EventStatus.PUBLISHED,        false),
                    pub( "Cryptocurrency & Digital Assets","Understanding crypto investments.",                         23,  6, EventStatus.PUBLISHED,        false),
                    priv("Private Banking Forum",          "Wealth management for high-net-worth individuals.",         51,  6, EventStatus.PUBLISHED,        false),
                    pub( "Financial Literacy Workshop",    "Free — foundations of personal finance.",                    9,  4, EventStatus.DRAFT,            true),
                    pub( "Stock Market Investing Guide",   "Beginner's guide to the Nigerian stock exchange.",         -24,  5, EventStatus.PUBLISHED,        false)
            ),

            // Uche — Community Development  (index 17)
            List.of(
                    pub( "Community Leadership Forum",     "Building stronger, more connected communities.",            36,  7, EventStatus.PUBLISHED,        false),
                    pub( "Social Impact Workshop",         "Creating positive change in your community.",               19,  5, EventStatus.PUBLISHED,        false),
                    priv("NGO Strategic Planning",         "Effective non-profit management and governance.",           27,  8, EventStatus.PUBLISHED,        false),
                    pub( "Volunteerism & Giving Expo",     "Free — ways to make a difference.",                          10,  5, EventStatus.DRAFT,            true),
                    pub( "Community Health Initiative",    "Public health and wellness in local areas.",               -20,  6, EventStatus.PUBLISHED,        false)
            ),

            // Vicky — Cultural Exchange  (index 18)
            List.of(
                    pub( "African Culture Festival",       "Celebrating the diversity of African heritage.",            40,  8, EventStatus.PUBLISHED,        false),
                    pub( "Traditional Music & Dance",      "Exploring African rhythms and movement.",                   24,  6, EventStatus.PUBLISHED,        false),
                    priv("Cultural Ambassadors Gala",      "Free — celebrating cultural excellence.",                   29,  5, EventStatus.PUBLISHED,        true),
                    pub( "Language & Heritage Symposium", "Preserving African languages and traditions.",               47,  7, EventStatus.PENDING_APPROVAL, false),
                    pub( "Diaspora Dialogue",              "Connecting Africans at home and abroad.",                  -30,  5, EventStatus.PUBLISHED,        false)
            ),

            // Wuraola — Innovation & Entrepreneurship  (index 19)
            List.of(
                    pub( "Innovation Expo 2026",           "Showcasing game-changing technologies and ideas.",          48,  8, EventStatus.PUBLISHED,        false),
                    pub( "Deep Tech Bootcamp",             "Advanced technology for solving real problems.",             26,  7, EventStatus.PUBLISHED,        false),
                    priv("Innovation Board Meeting",       "Strategic discussions on future technologies.",             53,  4, EventStatus.PUBLISHED,        false),
                    pub( "Prototyping Workshop",           "Free — from idea to prototype in one day.",                  15,  5, EventStatus.DRAFT,            true),
                    pub( "Tech for Good Summit",           "Innovation solving social problems.",                      -25,  6, EventStatus.PUBLISHED,        false)
            ),

            // Xolani — Entrepreneurship Summit  (index 20)
            List.of(
                    pub( "Annual Entrepreneurs Summit",    "The largest gathering of business builders.",               45,  8, EventStatus.PUBLISHED,        false),
                    pub( "Pre-Launch Bootcamp",            "Getting your startup ready for market.",                    19,  6, EventStatus.PUBLISHED,        false),
                    priv("Founders' Mastermind Group",     "Exclusive peer learning for experienced founders.",         34,  8, EventStatus.PUBLISHED,        false),
                    pub( "Lean Startup Workshop",          "Free — building lean, customer-centric businesses.",        11,  4, EventStatus.DRAFT,            true),
                    pub( "Exit Strategy Forum",            "Planning and executing successful business exits.",        -32,  5, EventStatus.PUBLISHED,        false)
            ),

            // Yetunde — Expo & Events  (index 21)
            List.of(
                    pub( "Major Trade Expo 2026",          "The largest gathering of vendors and buyers.",              46,  8, EventStatus.PUBLISHED,        false),
                    pub( "Consumer Products Showcase",     "Latest innovations in consumer goods.",                     21,  6, EventStatus.PUBLISHED,        false),
                    priv("VIP Networking Reception",       "Free — exclusive for exhibitors and partners.",             25,  3, EventStatus.PUBLISHED,        true),
                    pub( "E-commerce Marketplace Forum",   "Selling online and growing your business.",                 50,  7, EventStatus.PENDING_APPROVAL, false),
                    pub( "Vendor Training Summit",         "Skills for succeeding as a vendor or merchant.",           -28,  5, EventStatus.PUBLISHED,        false)
            ),

            // Zainab — Conference Series  (index 22)
            List.of(
                    pub( "Annual Business Conference",     "Insights from industry leaders and innovators.",            41,  8, EventStatus.PUBLISHED,        false),
                    pub( "Leadership Development Program", "Strategies for leading in uncertain times.",                18,  6, EventStatus.PUBLISHED,        false),
                    priv("Executive Breakfast Series",     "Free — intimate sessions with C-suite leaders.",           30,  2, EventStatus.PUBLISHED,        true),
                    pub( "Change Management Workshop",     "Leading transformation in your organization.",              44,  7, EventStatus.PENDING_APPROVAL, false),
                    pub( "Strategic Planning Retreat",     "Annual strategy and planning session.",                    -26,  8, EventStatus.PUBLISHED,        false)
            ),

            // Amara — Professional Development Workshop  (index 23)
            List.of(
                    pub( "Skills Development Bootcamp",    "Upskilling for the modern workplace.",                      35,  7, EventStatus.PUBLISHED,        false),
                    pub( "Leadership Coaching Program",    "Personal coaching for emerging leaders.",                   17,  6, EventStatus.PUBLISHED,        false),
                    priv("Professional Certification Course","Earn recognized credentials in your field.",              43,  8, EventStatus.PUBLISHED,        false),
                    pub( "Career Transition Workshop",     "Free — navigating career changes.",                           7,  4, EventStatus.DRAFT,            true),
                    pub( "Workplace Communication Summit", "Effective communication at all levels.",                   -19,  5, EventStatus.PUBLISHED,        false)
            ),

            // Bolaji — Business Forum  (index 24)
            List.of(
                    pub( "Annual Business Leaders Forum",  "Networking and insights for business owners.",              43,  8, EventStatus.PUBLISHED,        false),
                    pub( "Corporate Governance Summit",    "Best practices in business governance.",                    20,  6, EventStatus.PUBLISHED,        false),
                    priv("Board Members' Roundtable",      "Strategic discussions for board leadership.",              52,  4, EventStatus.PUBLISHED,        false),
                    pub( "Business Ethics & Compliance",   "Free — navigating regulatory requirements.",               13,  5, EventStatus.DRAFT,            true),
                    pub( "Franchise Opportunity Expo",     "Exploring franchise business models.",                    -29,  6, EventStatus.PUBLISHED,        false)
            ),

            // Chidinma — Festival Celebration  (index 25)
            List.of(
                    pub( "Annual Celebration Festival",    "A festival of food, music, and community.",                 50,  8, EventStatus.PUBLISHED,        false),
                    pub( "Cultural Food Festival",         "Traditional cuisines from across the continent.",           27,  6, EventStatus.PUBLISHED,        false),
                    priv("Festival VIP Lounge",            "Free — exclusive festival experience.",                     26,  4, EventStatus.PUBLISHED,        true),
                    pub( "Music & Entertainment Gala",     "Live performances and entertainment.",                      55,  7, EventStatus.PENDING_APPROVAL, false),
                    pub( "Community Celebration Carnival", "Free — a day of fun for the whole family.",               -33,  8, EventStatus.PUBLISHED,       true)
            ),

            // Daniel — Retreat & Renewal  (index 26)
            List.of(
                    pub( "Annual Wellness Retreat",        "Renewal and restoration in a peaceful setting.",            60,  3, EventStatus.PUBLISHED,        false),
                    pub( "Mindfulness & Meditation Workshop","Transform your inner peace and clarity.",                28,  7, EventStatus.PUBLISHED,        false),
                    priv("Executive Retreat Getaway",      "Strategic planning in a serene setting.",                   38,  3, EventStatus.PUBLISHED,        false),
                    pub( "Nature & Wellness Escape",       "Free — connect with nature and self.",                       14,  3, EventStatus.DRAFT,            true),
                    pub( "Team Building Retreat",          "Strengthen bonds and align team goals.",                   -21,  3, EventStatus.PUBLISHED,        false)
            ),

            // Ebube — Gala Dinner Event  (index 27)
            List.of(
                    pub( "Annual Charity Gala Dinner",     "Fine dining benefiting local charities.",                   52,  4, EventStatus.PUBLISHED,        false),
                    pub( "Awards & Recognition Gala",      "Celebrating excellence and achievements.",                  30,  4, EventStatus.PUBLISHED,        false),
                    priv("VIP Gala Reception",             "Exclusive evening for VIP patrons.",                        36,  4, EventStatus.PUBLISHED,        false),
                    pub( "Black Tie Dinner Benefit",       "Elegant dinner supporting social causes.",                 48,  4, EventStatus.PENDING_APPROVAL, false),
                    pub( "Anniversary Celebration Gala",   "Commemorating milestones and memories.",                  -31,  4, EventStatus.PUBLISHED,        false)
            ),

            // Folake — Symposium & Research  (index 28)
            List.of(
                    pub( "Annual Research Symposium",      "Sharing latest findings and innovations.",                 40,  8, EventStatus.PUBLISHED,        false),
                    pub( "Academic Excellence Forum",      "Advancing knowledge and critical thinking.",                22,  6, EventStatus.PUBLISHED,        false),
                    priv("Research Collaboration Summit",  "Networking for academic and research professionals.",       54,  8, EventStatus.PUBLISHED,        false),
                    pub( "Student Research Showcase",      "Free — highlighting student research projects.",           6,  5, EventStatus.DRAFT,            true),
                    pub( "Think Tank Discussion Series",   "Deep dives into critical societal issues.",               -23,  6, EventStatus.PUBLISHED,        false)
            ),

            // Gbemi — Carnival & Entertainment  (index 29)
            List.of(
                    pub( "Annual Street Carnival",         "Music, dance, food, and celebration.",                     51,  8, EventStatus.PUBLISHED,        false),
                    pub( "Entertainment & Comedy Fest",    "Laughter and entertainment all day long.",                  28,  6, EventStatus.PUBLISHED,        false),
                    priv("Behind-the-Scenes Carnival Tour", "Free — exclusive carnival access and tours.",              22,  4, EventStatus.PUBLISHED,        true),
                    pub( "Carnival Parade & Showcase",     "Grand parade with floats and performers.",                 57,  6, EventStatus.PENDING_APPROVAL, false),
                    pub( "Family Fun Day Carnival",        "Free — entertainment for all ages.",                       -34,  8, EventStatus.PUBLISHED,       true)
            ),

            // Habiba — Awards Ceremony  (index 30)
            List.of(
                    pub( "Annual Excellence Awards",       "Honoring achievers and leaders.",                           47,  4, EventStatus.PUBLISHED,        false),
                    pub( "Industry Innovation Awards",     "Recognizing the best innovations.",                         25,  4, EventStatus.PUBLISHED,        false),
                    priv("Awards Gala Dinner",             "Exclusive celebration for award recipients.",              37,  4, EventStatus.PUBLISHED,        false),
                    pub( "Community Heroes Recognition",   "Free — celebrating unsung heroes.",                         16,  3, EventStatus.DRAFT,            true),
                    pub( "Lifetime Achievement Awards",    "Celebrating legendary contributions.",                    -27,  4, EventStatus.PUBLISHED,        false)
            ),

            // Ibrahim — Showcase & Demonstration  (index 31)
            List.of(
                    pub( "Product Launch Showcase",        "Introducing latest innovations.",                          42,  6, EventStatus.PUBLISHED,        false),
                    pub( "Live Demo & Presentation Event", "Interactive product demonstrations.",                      24,  5, EventStatus.PUBLISHED,        false),
                    priv("Early Access Preview",           "Free — exclusive first look at new products.",             32,  3, EventStatus.PUBLISHED,        true),
                    pub( "Technology Showcase Fair",       "See the future of technology today.",                      58,  8, EventStatus.PENDING_APPROVAL, false),
                    pub( "Innovation Marketplace",         "Explore groundbreaking solutions.",                        -29,  6, EventStatus.PUBLISHED,        false)
            )
    );

    // ─── programme seed data ─────────────────────────────────────────────────
    //
    // (organiserIndex, eventIndex, offsetMinutes from event start, durationMinutes)

    private record ProgSeed(
            int organiserIndex, int eventIndex,
            String title, String description,
            String speakerName, String speakerBio,
            int offsetMinutes, int durationMinutes,
            int displayOrder) {}

    private static final List<ProgSeed> PROGRAMME_SEEDS = List.of(

            // TechFest Lagos 2026 (Akin, event 0) — full-day conference
            new ProgSeed(0, 0, "Opening Keynote: The Future of African Tech",
                    "Setting the vision for a decade of African-led technology innovation.",
                    "Akin Ogundimu", "Serial entrepreneur and co-founder of three Lagos-based startups.",
                    0, 60, 1),
            new ProgSeed(0, 0, "Panel: AI in Emerging Markets",
                    "How artificial intelligence is reshaping healthcare, finance, and agriculture across Africa.",
                    "Dr. Amara Nwosu", "AI researcher at the African Institute for Mathematical Sciences.",
                    70, 90, 2),
            new ProgSeed(0, 0, "Workshop: Building Scalable APIs with Spring Boot",
                    "Hands-on session — attendees build and deploy a production-ready REST API.",
                    "Tunde Babatunde", "Senior Backend Engineer at Flutterwave.",
                    180, 120, 3),
            new ProgSeed(0, 0, "Fireside Chat: From Idea to Series A",
                    "Three founders share candid lessons from raising their first institutional rounds.",
                    "Zainab Musa", "Investor and founding partner at Lagos Ventures.",
                    330, 60, 4),
            new ProgSeed(0, 0, "Closing: Networking Reception",
                    "Open networking with refreshments provided by our catering partner.",
                    null, null,
                    420, 60, 5),

            // AI & Machine Learning Summit (Akin, event 1)
            new ProgSeed(0, 1, "Welcome & State of AI in Nigeria",
                    "A data-driven overview of AI adoption across Nigerian industries.",
                    "Chidi Eze", "Head of Data Science at Access Bank.",
                    0, 45, 1),
            new ProgSeed(0, 1, "Deep Learning for Healthcare Diagnostics",
                    "Case study: using convolutional neural networks to detect malaria from microscopy images.",
                    "Prof. Ngozi Adeyemi", "Professor of Computational Biology, University of Lagos.",
                    55, 60, 2),
            new ProgSeed(0, 1, "Ethics & Bias in AI Systems",
                    "Understanding and mitigating bias when training on African datasets.",
                    "Fatima Al-Hassan", "AI Policy Researcher, Carnegie Mellon Africa.",
                    125, 45, 3),
            new ProgSeed(0, 1, "Live Demo: Building a Chatbot with LLMs",
                    "From zero to working chatbot — live coding session in Python.",
                    "Emeka Obi", "Staff ML Engineer, Google.",
                    180, 90, 4),

            // Afrobeats Night Out (Chioma, event 0)
            new ProgSeed(1, 0, "Doors Open & DJ Set",
                    "Welcome set from resident DJ spinning classic and contemporary Afrobeats.",
                    "DJ Kemi", "Lagos-based DJ and producer with 10+ years on the scene.",
                    0, 60, 1),
            new ProgSeed(1, 0, "Opening Act: Emerging Artists Showcase",
                    "Three up-and-coming acts get 20 minutes each to show what they've got.",
                    null, null,
                    60, 60, 2),
            new ProgSeed(1, 0, "Headline Performance",
                    "The evening's headline act takes the stage for a full 90-minute set.",
                    "Seun Kuti", "Afrobeat legend and son of the Afrobeat king Fela Kuti.",
                    150, 90, 3),

            // Entrepreneurs Summit 2026 (Emeka, event 0)
            new ProgSeed(2, 0, "Registration & Morning Coffee",
                    "Arrival, badge collection, and informal networking over coffee.",
                    null, null,
                    -30, 30, 1),
            new ProgSeed(2, 0, "State of Entrepreneurship in Nigeria",
                    "Annual benchmarking report: funding, exits, and sector trends.",
                    "Bola Okonkwo", "CEO, Nigerian Startup Alliance.",
                    0, 50, 2),
            new ProgSeed(2, 0, "Panel: Access to Capital — Debt vs. Equity",
                    "Founders and investors debate the right financing mix at each growth stage.",
                    "Adaeze Nnamdi", "Partner, Ventures Platform.",
                    60, 75, 3),
            new ProgSeed(2, 0, "Masterclass: Building a Sales Engine from Scratch",
                    "Practical frameworks for early-stage B2B sales — pipeline, cadence, and closing.",
                    "Jide Afolabi", "VP Sales, Paystack.",
                    150, 60, 4),
            new ProgSeed(2, 0, "Pitch Competition: Top 5 Startups",
                    "Five finalists pitch to a live panel of investors for a ₦5M prize.",
                    null, null,
                    240, 90, 5),
            new ProgSeed(2, 0, "Awards & Networking Dinner",
                    "Entrepreneur of the Year awards followed by a sit-down dinner.",
                    null, null,
                    360, 120, 6),

            // SME Growth Conference (Emeka, event 1 — private)
            new ProgSeed(2, 1, "Welcome & Introductions",
                    "Brief introductions from each attendee — curated group of 40 SME owners.",
                    null, null,
                    0, 30, 1),
            new ProgSeed(2, 1, "Financial Management for SMEs",
                    "Understanding cash flow, credit, and working capital for small businesses.",
                    "Tosin Adesanya", "Chartered Accountant and SME Finance Advisor.",
                    30, 90, 2),
            new ProgSeed(2, 1, "Digital Marketing on a Budget",
                    "How to build a brand and acquire customers with ₦50k/month or less.",
                    "Bukola Adekunle", "Founder, Digital Bridge Agency.",
                    135, 60, 3),
            new ProgSeed(2, 1, "Open Q&A and Action Planning",
                    "Attendees workshop their own business challenges with expert guidance.",
                    null, null,
                    210, 75, 4),

            // Lagos Food & Wine Festival (Funke, event 0)
            new ProgSeed(3, 0, "Festival Opens — Culinary Village",
                    "Explore 30+ food stalls representing every region of Nigeria.",
                    null, null,
                    0, 120, 1),
            new ProgSeed(3, 0, "Chef Demo: Modern Nigerian Cuisine",
                    "Live cooking demonstration blending traditional recipes with global techniques.",
                    "Chef Sola Williams", "Executive Chef, Oriental Hotel Lagos.",
                    120, 60, 2),
            new ProgSeed(3, 0, "Wine Pairing Masterclass",
                    "Discovering Nigerian and international wines that complement West African cuisine.",
                    "Amaka Osei", "Certified Sommelier.",
                    200, 90, 3),
            new ProgSeed(3, 0, "Street Food Cook-Off Competition",
                    "Eight vendors compete for the People's Choice Award voted live by attendees.",
                    null, null,
                    300, 120, 4),

            // Nollywood Film Premiere (Ifeanyi, event 1 — private)
            new ProgSeed(6, 1, "Red Carpet Arrivals",
                    "Press and VIP guests arrive on the red carpet — photography and interviews.",
                    null, null,
                    -60, 60, 1),
            new ProgSeed(6, 1, "Welcome Remarks from the Director",
                    "The director introduces the film, the cast, and the story behind the project.",
                    "Kunle Afolayan", "Acclaimed Nollywood director and producer.",
                    0, 15, 2),
            new ProgSeed(6, 1, "Film Screening",
                    "World premiere screening of the feature film.",
                    null, null,
                    15, 120, 3),
            new ProgSeed(6, 1, "Cast Q&A",
                    "The director and lead cast take questions from the audience.",
                    null, null,
                    145, 30, 4),
            new ProgSeed(6, 1, "After-Party",
                    "Cocktails and canapes — private reception for cast, crew, and invited guests.",
                    null, null,
                    185, 90, 5),

            // Women in STEM Conference (Halima, event 0)
            new ProgSeed(5, 0, "Opening Keynote: Why Representation Matters",
                    "Exploring the systemic barriers and the business case for diversity in STEM.",
                    "Dr. Aisha Umar", "Deputy Director General, NITDA.",
                    0, 50, 1),
            new ProgSeed(5, 0, "Workshop: Breaking into Big Tech",
                    "CV clinic, interview prep, and networking strategies for women in engineering.",
                    "Ngozi Akpan", "Senior SWE at Microsoft Africa.",
                    60, 90, 2),
            new ProgSeed(5, 0, "Panel: Navigating the Workplace as a Woman in STEM",
                    "Four senior leaders share stories, strategies, and hard-won advice.",
                    null, null,
                    165, 75, 3),
            new ProgSeed(5, 0, "Mentorship Speed Networking",
                    "30 mentors, 10 minutes each — structured mentorship matching session.",
                    null, null,
                    255, 90, 4),

            // Lagos Fashion Week (Lara, event 0)
            new ProgSeed(9, 0, "Doors Open & Cocktail Reception",
                    "Welcome reception in the foyer — champagne and canapés.",
                    null, null,
                    -30, 30, 1),
            new ProgSeed(9, 0, "Opening Collection: Heritage Reborn",
                    "Lara Williams' signature collection inspired by Yoruba textile traditions.",
                    null, null,
                    0, 45, 2),
            new ProgSeed(9, 0, "Guest Designer: Contemporary African Streetwear",
                    "Lagos-based streetwear label Àṣà presents their debut runway collection.",
                    null, null,
                    55, 40, 3),
            new ProgSeed(9, 0, "Headline Show: Futurewear 2030",
                    "The festival's headline show — 10 designers, one cohesive vision.",
                    null, null,
                    110, 75, 4),
            new ProgSeed(9, 0, "Closing & Press Interviews",
                    "Press access for interviews with participating designers.",
                    null, null,
                    195, 45, 5),

            // Digital Marketing Summit 2026 (Musa, event 0)
            new ProgSeed(10, 0, "Welcome & State of Digital Marketing",
                    "An overview of digital marketing trends in Nigeria and across Africa.",
                    "Musa Ibrahim", "Head of Growth, Jumia Nigeria.",
                    0, 45, 1),
            new ProgSeed(10, 0, "Masterclass: SEO & Content Strategy",
                    "Proven tactics for ranking higher and driving organic traffic.",
                    "Tobi Fashola", "SEO Lead, Paystack.",
                    55, 75, 2),
            new ProgSeed(10, 0, "Panel: Social Media ROI — Myth or Reality?",
                    "Marketers debate metrics, attribution, and what actually drives conversions.",
                    "Amaka Chukwu", "Digital Marketing Director, MTN Nigeria.",
                    140, 60, 3),
            new ProgSeed(10, 0, "Workshop: Google Ads & Meta for SMEs",
                    "Hands-on ad creation and targeting — bring your business, leave with a live campaign.",
                    "Chuka Nwosu", "Certified Google Partner & Trainer.",
                    210, 90, 4),
            new ProgSeed(10, 0, "Networking & Cocktail Hour",
                    "Open networking for attendees and speakers.",
                    null, null,
                    330, 60, 5),

            // Growth Hacking Workshop (Musa, event 1)
            new ProgSeed(10, 1, "The Growth Hacking Mindset",
                    "Why traditional marketing fails startups and what to do instead.",
                    "Seun Akerele", "Growth Lead, Flutterwave.",
                    0, 50, 1),
            new ProgSeed(10, 1, "Viral Loops & Referral Programs",
                    "Designing growth loops that compound — case studies from Piggyvest and Cowrywise.",
                    "Ngozi Eze", "Product Growth Manager, PiggyVest.",
                    60, 60, 2),
            new ProgSeed(10, 1, "Live Hacking Session",
                    "Teams compete to design a growth strategy for a fictional startup in 45 minutes.",
                    null, null,
                    130, 90, 3),

            // Media Production Conference (Ngozi, event 0)
            new ProgSeed(11, 0, "Opening: The Future of African Media",
                    "How Nigeria's media industry is evolving in the streaming age.",
                    "Ngozi Okonkwo", "CEO, MultiChoice Nigeria.",
                    0, 50, 1),
            new ProgSeed(11, 0, "Panel: Monetising Content in Nigeria",
                    "YouTube, TikTok, Podcasts — where creators are making real money.",
                    "Korty EO", "Award-winning documentary filmmaker.",
                    60, 75, 2),
            new ProgSeed(11, 0, "Workshop: Broadcast Production on a Budget",
                    "Practical techniques for professional-quality video with minimal equipment.",
                    "Chidi Okeke", "Production Director, Channels TV.",
                    145, 90, 3),
            new ProgSeed(11, 0, "Screening: Best of Nigerian Indie Film 2026",
                    "Curated short films and documentaries from emerging Nigerian filmmakers.",
                    null, null,
                    245, 75, 4),

            // Podcast & Audio Summit (Ngozi, event 1)
            new ProgSeed(11, 1, "The Audio Revolution in Africa",
                    "Why podcasting is exploding and what it means for brands and creators.",
                    "Kunle Soname", "Host, The Business Day Podcast.",
                    0, 45, 1),
            new ProgSeed(11, 1, "Monetise Your Podcast",
                    "Sponsorships, Patreon, and premium content — realistic paths to revenue.",
                    "Adaeze Nnaji", "Top 10 Nigerian Podcast Host.",
                    55, 60, 2),
            new ProgSeed(11, 1, "Live Recording Demo & Q&A",
                    "Experience a live podcast recording and ask the experts anything.",
                    null, null,
                    125, 75, 3),

            // Holistic Health Expo 2026 (Oluwatoyin, event 0)
            new ProgSeed(12, 0, "Opening: Holistic Health — A Nigerian Perspective",
                    "Integrating traditional and modern approaches to well-being.",
                    "Dr. Yetunde Obe", "Consultant Physician & Wellness Advocate.",
                    0, 45, 1),
            new ProgSeed(12, 0, "Workshop: Stress Management for Busy Professionals",
                    "Evidence-based tools for managing stress, anxiety, and burnout.",
                    "Chinyere Obi", "Clinical Psychologist.",
                    55, 60, 2),
            new ProgSeed(12, 0, "Panel: Mental Health, Nutrition & Movement",
                    "Experts discuss the interconnected pillars of holistic health.",
                    "Dr. Babatunde Adeyemi", "Sports Medicine Specialist.",
                    125, 75, 3),
            new ProgSeed(12, 0, "Live Yoga & Breathwork Session",
                    "Guided 30-minute session — mats provided for the first 200 attendees.",
                    "Adaora Bello", "Certified Yoga Instructor.",
                    210, 45, 4),
            new ProgSeed(12, 0, "Exhibition & Product Demos",
                    "Explore wellness brands, supplements, and fitness equipment.",
                    null, null,
                    270, 90, 5),

            // Fitness Challenge Series (Oluwatoyin, event 1)
            new ProgSeed(12, 1, "Kick-Off: 12 Weeks to a Better You",
                    "Programme overview, goal setting, and team formation.",
                    "Coach Olusegun Bello", "NSCA Certified Strength Coach.",
                    0, 50, 1),
            new ProgSeed(12, 1, "Movement Assessment & Baseline Testing",
                    "Participants undergo fitness tests to establish starting benchmarks.",
                    null, null,
                    60, 90, 2),

            // Contemporary Art Exhibition (Precious, event 0)
            new ProgSeed(13, 0, "Exhibition Opens — Curator's Welcome",
                    "The curator introduces the 20 featured artists and the exhibition theme.",
                    "Precious Nwankwo", "Curator & Founder, Lagos Art Collective.",
                    0, 30, 1),
            new ProgSeed(13, 0, "Artist Talk: Process & Inspiration",
                    "Three featured artists discuss their creative journeys.",
                    "Tunde Afolabi", "Mixed-media artist.",
                    40, 60, 2),
            new ProgSeed(13, 0, "Live Painting Performance",
                    "Watch an artist create a large-format piece in real time.",
                    "Emeka Uche", "Live performance artist.",
                    110, 45, 3),
            new ProgSeed(13, 0, "Collectors' Q&A & Private Viewing",
                    "Guided walkthrough for collectors — acquisition enquiries welcome.",
                    null, null,
                    165, 60, 4),

            // Sculpture & Installation Art (Precious, event 1)
            new ProgSeed(13, 1, "Opening Remarks & Exhibition Walkthrough",
                    "Curator-led tour of the 15-piece sculpture and installation collection.",
                    "Dr. Amara Okafor", "Art Historian, University of Lagos.",
                    0, 45, 1),
            new ProgSeed(13, 1, "Panel: The Market for Contemporary African Sculpture",
                    "Collectors, gallery owners, and artists discuss valuation and demand.",
                    null, null,
                    55, 60, 2),

            // Supply Chain Optimization Forum (Rasheed, event 0)
            new ProgSeed(14, 0, "Keynote: Nigeria's Logistics Landscape 2026",
                    "An honest assessment of infrastructure gaps and emerging solutions.",
                    "Rasheed Yusuf", "CEO, SwiftMoves Logistics.",
                    0, 50, 1),
            new ProgSeed(14, 0, "Panel: Last-Mile Delivery — Solving the Final Challenge",
                    "How companies are cracking same-day and next-day delivery in Lagos.",
                    "Amina Suleiman", "COO, Kwik Delivery.",
                    60, 75, 2),
            new ProgSeed(14, 0, "Workshop: Warehouse Layout & Inventory Optimization",
                    "Practical layout design and inventory management for SME warehouses.",
                    "Engr. Oluwafemi Ade", "Industrial Engineer, DHL Nigeria.",
                    145, 90, 3),
            new ProgSeed(14, 0, "Tech Demo: TMS & WMS Platforms for Growing Businesses",
                    "Live demos of transport management and warehouse systems.",
                    null, null,
                    245, 60, 4),

            // Warehouse Management Summit (Rasheed, event 1)
            new ProgSeed(14, 1, "State of Warehousing in Nigeria",
                    "Data on storage capacity, occupancy, and forecast demand.",
                    "Dr. Kunle Lawal", "Economist, Lagos Chamber of Commerce.",
                    0, 45, 1),
            new ProgSeed(14, 1, "Cold Chain & Perishables Management",
                    "Maintaining product integrity from farm to final mile.",
                    "Bisi Ogunbamowo", "Cold Chain Manager, PZ Cussons.",
                    55, 60, 2),

            // Web3 & Blockchain Summit (Seun, event 0)
            new ProgSeed(15, 0, "Opening: The State of Web3 in Africa",
                    "Africa's blockchain ecosystem — adoption, challenges, and potential.",
                    "Seun Akanji", "Blockchain Researcher & Founder, ChainAfrica.",
                    0, 50, 1),
            new ProgSeed(15, 0, "Workshop: Building Your First dApp on Ethereum",
                    "Hands-on development workshop — Solidity basics to a deployed smart contract.",
                    "Chukwuemeka Obi", "Lead Smart Contract Developer, Binance Africa.",
                    60, 120, 2),
            new ProgSeed(15, 0, "Panel: DeFi — Opportunity or Trap?",
                    "Investors, developers, and regulators debate decentralised finance in Nigeria.",
                    "Kola Adedeji", "Director, SEC Nigeria Digital Assets Division.",
                    190, 75, 3),
            new ProgSeed(15, 0, "NFT & Digital Ownership for African Creators",
                    "How artists, musicians, and filmmakers are monetising through NFTs.",
                    "DJ Neptune", "Afrobeats artist and NFT pioneer.",
                    275, 60, 4),

            // Cloud Architecture Masterclass (Seun, event 1)
            new ProgSeed(15, 1, "Cloud-Native Design Principles",
                    "12-factor apps, microservices, and designing for resilience.",
                    "Taiwo Adeyemi", "Principal Cloud Architect, AWS Africa.",
                    0, 75, 1),
            new ProgSeed(15, 1, "Hands-On: Deploying a Scalable API on AWS",
                    "Build and deploy a production-grade REST API from scratch.",
                    "Folake Bello", "Senior DevOps Engineer, Interswitch.",
                    85, 90, 2),
            new ProgSeed(15, 1, "Cost Optimisation & FinOps Best Practices",
                    "Reduce your cloud bill without sacrificing performance.",
                    null, null,
                    185, 60, 3),

            // Investment Summit 2026 (Tunde, event 0)
            new ProgSeed(16, 0, "Opening: The Investment Climate in Nigeria 2026",
                    "Macroeconomic overview and what it means for individual and institutional investors.",
                    "Tunde Okafor", "Chief Investment Officer, Stanbic IBTC.",
                    0, 50, 1),
            new ProgSeed(16, 0, "Panel: Fixed Income vs. Equities — Where to Put Your Money",
                    "Portfolio managers debate asset allocation in a high-inflation environment.",
                    "Nkechi Osei", "MD, Chapel Hill Denham.",
                    60, 75, 2),
            new ProgSeed(16, 0, "Masterclass: Building a ₦10M Portfolio from Scratch",
                    "Step-by-step framework for new retail investors.",
                    "Ayo Martins", "Certified Financial Planner.",
                    145, 90, 3),
            new ProgSeed(16, 0, "Demo: Using Nigerian Fintech Apps for Investing",
                    "Live demos of Bamboo, Trove, and Risevest.",
                    null, null,
                    245, 60, 4),

            // Cryptocurrency & Digital Assets (Tunde, event 1)
            new ProgSeed(16, 1, "Crypto 101 for Nigerian Investors",
                    "What is crypto, how does it work, and is it legal in Nigeria?",
                    "Bolu Adesina", "Crypto Analyst & Educator.",
                    0, 45, 1),
            new ProgSeed(16, 1, "Technical Analysis & Trading Strategies",
                    "Reading charts, identifying patterns, and managing risk.",
                    "Segun Olatunji", "Professional Crypto Trader.",
                    55, 75, 2),
            new ProgSeed(16, 1, "Panel: Stablecoins & Cross-Border Payments",
                    "How USDT and USDC are transforming remittances for Nigerians.",
                    null, null,
                    140, 60, 3),

            // Community Leadership Forum (Uche, event 0)
            new ProgSeed(17, 0, "Opening: Community Power in the Digital Age",
                    "How technology is amplifying grassroots community action.",
                    "Uche Anyanwu", "Executive Director, Lagos Community Foundation.",
                    0, 45, 1),
            new ProgSeed(17, 0, "Panel: Bridging the Urban-Rural Development Gap",
                    "Leaders from Lagos, Kano, and Enugu share contrasting realities.",
                    "Mallam Bello Umar", "Local Government Chairman, Nassarawa LGA.",
                    55, 75, 2),
            new ProgSeed(17, 0, "Workshop: Designing Community Projects That Stick",
                    "Frameworks for sustainable community initiatives with limited budgets.",
                    "Dr. Adunola Ogunfemi", "Community Development Consultant.",
                    140, 90, 3),

            // Social Impact Workshop (Uche, event 1)
            new ProgSeed(17, 1, "Theory of Change — Making Your Idea Actionable",
                    "Turn vague social goals into measurable, funded programmes.",
                    "Emeka Nwosu", "Director of Programmes, Tony Elumelu Foundation.",
                    0, 60, 1),
            new ProgSeed(17, 1, "Funding Your Impact: Grants, CSR & Crowdfunding",
                    "Practical guide to accessing funds for social enterprises.",
                    "Chinwe Okoye", "Grants Consultant.",
                    70, 75, 2),

            // African Culture Festival (Vicky, event 0)
            new ProgSeed(18, 0, "Grand Opening Procession",
                    "Cultural performers from 10 Nigerian states open the festival.",
                    null, null,
                    0, 45, 1),
            new ProgSeed(18, 0, "Heritage Village Walkthrough",
                    "Explore 12 cultural booths showcasing food, craft, and tradition.",
                    "Vicky Osei", "Festival Director & Cultural Anthropologist.",
                    45, 120, 2),
            new ProgSeed(18, 0, "Panel: Preserving African Culture in a Globalised World",
                    "How do we keep traditions alive while embracing modernity?",
                    "Prof. Bisi Fayemi", "Dept. of African Studies, UNILAG.",
                    175, 75, 3),
            new ProgSeed(18, 0, "Evening Performances — Music & Dance",
                    "Live performances by traditional and contemporary musicians.",
                    null, null,
                    270, 120, 4),

            // Traditional Music & Dance (Vicky, event 1)
            new ProgSeed(18, 1, "Opening: The Rhythm of Africa",
                    "Introduction to West African percussion traditions.",
                    "Baba Seun", "Master Drummer, Egungun Society.",
                    0, 30, 1),
            new ProgSeed(18, 1, "Dance Workshop — Bata, Swange & Atilogwu",
                    "Hands-on dance sessions with instructors from three Nigerian traditions.",
                    null, null,
                    40, 90, 2),
            new ProgSeed(18, 1, "Fusion Performance: Traditional Meets Contemporary",
                    "Watch musicians and dancers blend ancestral forms with modern expression.",
                    null, null,
                    140, 60, 3),

            // Innovation Expo 2026 (Wuraola, event 0)
            new ProgSeed(19, 0, "Opening Keynote: The Nigerian Innovation Decade",
                    "Why the next wave of global innovation will be shaped by Africa.",
                    "Wuraola Lawal", "Chief Innovation Officer, Guaranty Trust Bank.",
                    0, 50, 1),
            new ProgSeed(19, 0, "Start-up Pavilion Pitch Sessions",
                    "20 start-ups get 5 minutes each to demo their solutions to live investors.",
                    null, null,
                    60, 120, 2),
            new ProgSeed(19, 0, "Deep Dive: AgriTech & FoodTech in Nigeria",
                    "How technology is transforming food production and distribution.",
                    "Ngozi Adaeze", "CEO, FarmCrowdy.",
                    190, 60, 3),
            new ProgSeed(19, 0, "Demo Zone: Robotics, AI & IoT",
                    "Interactive demos from Nigeria's leading hardware and AI start-ups.",
                    null, null,
                    260, 90, 4),
            new ProgSeed(19, 0, "Awards: Most Innovative Startup 2026",
                    "Investor panel announces this year's winners across five categories.",
                    null, null,
                    370, 45, 5),

            // Deep Tech Bootcamp (Wuraola, event 1)
            new ProgSeed(19, 1, "Introduction to Deep Tech Sectors",
                    "AI, biotech, clean energy, advanced manufacturing — primer for beginners.",
                    "Dr. Kunle Afolabi", "Research Lead, Google DeepMind Africa.",
                    0, 60, 1),
            new ProgSeed(19, 1, "Problem-Solution Sprint",
                    "Teams identify a deep-tech solvable problem and prototype a solution concept.",
                    null, null,
                    70, 180, 2),
            new ProgSeed(19, 1, "Demo Day: Present Your Concept",
                    "Each team presents to a panel of deep-tech investors.",
                    null, null,
                    270, 90, 3),

            // Annual Entrepreneurs Summit (Xolani, event 0)
            new ProgSeed(20, 0, "Opening: The State of African Entrepreneurship",
                    "Data and stories from the 2025 African Startup Ecosystem Report.",
                    "Xolani Zuma", "Executive Director, African Business Council.",
                    0, 50, 1),
            new ProgSeed(20, 0, "Fireside Chat: From ₦50k to ₦50M",
                    "An honest conversation about the grinding early years of building a business.",
                    "Temitayo Ogunlesi", "Founder, Ogunlesi Holdings.",
                    60, 60, 2),
            new ProgSeed(20, 0, "Workshop: Systemising Your Business for Scale",
                    "SOPs, delegation, and the tools that free you to lead.",
                    "Busola Akin", "Business Systems Consultant.",
                    130, 90, 3),
            new ProgSeed(20, 0, "Investor Panel: What We Look for in 2026",
                    "Four VCs share what they're funding — and what they're passing on.",
                    null, null,
                    230, 75, 4),
            new ProgSeed(20, 0, "Networking Dinner",
                    "Sit-down dinner with curated table assignments for maximum networking.",
                    null, null,
                    330, 120, 5),

            // Pre-Launch Bootcamp (Xolani, event 1)
            new ProgSeed(20, 1, "Market Validation Masterclass",
                    "How to know if your idea is worth building before spending a naira.",
                    "Chidi Mmadu", "Lean Startup Facilitator.",
                    0, 75, 1),
            new ProgSeed(20, 1, "Building an MVP in 3 Weeks",
                    "No-code and low-code tools for rapid prototyping.",
                    "Ada Oguike", "Product Designer, Paystack.",
                    85, 90, 2),
            new ProgSeed(20, 1, "Your First 100 Customers",
                    "Guerrilla marketing and direct sales tactics for pre-launch growth.",
                    null, null,
                    185, 60, 3),

            // Major Trade Expo 2026 (Yetunde, event 0)
            new ProgSeed(21, 0, "Exhibition Hall Opens",
                    "Over 200 exhibitors across FMCG, manufacturing, agribusiness, and services.",
                    null, null,
                    0, 30, 1),
            new ProgSeed(21, 0, "Keynote: Intra-Africa Trade & AfCFTA Opportunities",
                    "Unlocking the ₦40 trillion African free trade opportunity.",
                    "Dr. Titi Alabi", "AfCFTA Advisor, Nigerian Government.",
                    40, 50, 2),
            new ProgSeed(21, 0, "B2B Matchmaking Sessions",
                    "Pre-scheduled 20-minute meetings between buyers and suppliers.",
                    null, null,
                    100, 240, 3),
            new ProgSeed(21, 0, "Panel: E-commerce & the Future of Retail",
                    "How online marketplaces are transforming the Nigerian retail landscape.",
                    "Juliet Anammah", "Former CEO, Jumia Nigeria.",
                    360, 75, 4),

            // Consumer Products Showcase (Yetunde, event 1)
            new ProgSeed(21, 1, "New Product Launch Showcase",
                    "30 brands launch new products in 5-minute live presentations.",
                    null, null,
                    0, 180, 1),
            new ProgSeed(21, 1, "Consumer Insights Panel",
                    "What Nigerian consumers want in 2026 — live research reveal.",
                    "Adaeze Obi", "Market Research Director, Nielsen Nigeria.",
                    190, 60, 2),

            // Annual Business Conference (Zainab, event 0)
            new ProgSeed(22, 0, "Opening Keynote: Leadership in Uncertainty",
                    "Navigating business disruptions with clarity and conviction.",
                    "Zainab Hassan", "MD, First Bank of Nigeria.",
                    0, 50, 1),
            new ProgSeed(22, 0, "Panel: Corporate Governance & Accountability",
                    "Board members, regulators, and executives on best governance practices.",
                    "Justice Kalu Anyim", "Former Chief Justice.",
                    60, 75, 2),
            new ProgSeed(22, 0, "Workshop: Managing High-Performance Teams",
                    "Tools and frameworks for leaders of growing organisations.",
                    "Femi Ade", "Organizational Psychologist.",
                    145, 90, 3),
            new ProgSeed(22, 0, "Closing: Awards for Business Excellence 2026",
                    "Recognising outstanding organisations across five sectors.",
                    null, null,
                    255, 60, 4),

            // Leadership Development Program (Zainab, event 1)
            new ProgSeed(22, 1, "Servant Leadership — Principles & Practice",
                    "Leading with empathy, humility, and purpose.",
                    "Rev. Tunde Bakare", "Pastor & Civic Leader.",
                    0, 60, 1),
            new ProgSeed(22, 1, "360° Feedback Exercise",
                    "Structured peer-feedback process with facilitated debrief.",
                    null, null,
                    70, 75, 2),

            // Skills Development Bootcamp (Amara, event 0)
            new ProgSeed(23, 0, "Welcome & Skills Audit",
                    "Participants assess their current skills and set personal development goals.",
                    null, null,
                    0, 45, 1),
            new ProgSeed(23, 0, "Communication & Presentation Skills",
                    "Practical exercises in public speaking, storytelling, and persuasion.",
                    "Amara Okafor", "Corporate Trainer & Coach.",
                    55, 90, 2),
            new ProgSeed(23, 0, "Critical Thinking & Problem Solving",
                    "Tools for structured analysis and decision-making under pressure.",
                    "Dr. Chinyere Uche", "Executive Coach.",
                    155, 75, 3),
            new ProgSeed(23, 0, "Technology Skills for the Modern Workplace",
                    "Excel, AI tools, and digital collaboration platforms.",
                    "Tayo Fashola", "Digital Transformation Lead.",
                    240, 90, 4),

            // Leadership Coaching Program (Amara, event 1)
            new ProgSeed(23, 1, "Self-Assessment: Your Leadership Style",
                    "Diagnostic questionnaire with facilitated debrief.",
                    null, null,
                    0, 60, 1),
            new ProgSeed(23, 1, "Coaching Fundamentals — Give & Receive Feedback",
                    "How to coach your team members and receive coaching gracefully.",
                    "Bukola Adetunji", "ICF Certified Coach.",
                    70, 90, 2),

            // Annual Business Leaders Forum (Bolaji, event 0)
            new ProgSeed(24, 0, "State of Nigerian Business 2026",
                    "Annual survey results: business confidence, talent, and technology.",
                    "Bolaji Oni", "Chairman, Lagos Chamber of Commerce.",
                    0, 50, 1),
            new ProgSeed(24, 0, "Panel: Accessing Growth Capital in a Tight Market",
                    "Banks, DFIs, and VCs on funding options for established businesses.",
                    "Funmi Ogunbiyi", "MD, Bank of Industry.",
                    60, 75, 2),
            new ProgSeed(24, 0, "Masterclass: Scaling Across Multiple Markets",
                    "Case study on expanding from Lagos to Kano, Abuja, and internationally.",
                    "Kemi Lala Akindoju", "Serial Entrepreneur.",
                    145, 90, 3),
            new ProgSeed(24, 0, "Networking Luncheon",
                    "Hosted lunch with roundtable discussions on key business themes.",
                    null, null,
                    260, 90, 4),

            // Corporate Governance Summit (Bolaji, event 1)
            new ProgSeed(24, 1, "Keynote: Why Good Governance is Good Business",
                    "Evidence from companies that thrive long-term through ethical governance.",
                    "Prof. Pat Utomi", "Economist & Governance Expert.",
                    0, 50, 1),
            new ProgSeed(24, 1, "Workshop: Structuring Your Board for Growth",
                    "Who to put on your board and how to run effective board meetings.",
                    "Abimbola Cole", "Corporate Lawyer.",
                    60, 90, 2),

            // Annual Celebration Festival (Chidinma, event 0)
            new ProgSeed(25, 0, "Festival Opening Ceremony",
                    "Cultural welcome, traditional blessing, and official launch.",
                    "Chidinma Obi", "Festival Founder & Cultural Director.",
                    0, 30, 1),
            new ProgSeed(25, 0, "Food Village: Taste of Nigeria",
                    "50+ food vendors representing every geopolitical zone of Nigeria.",
                    null, null,
                    30, 180, 2),
            new ProgSeed(25, 0, "Main Stage: Live Music Performances",
                    "Performances by afrobeats, fuji, and highlife artists.",
                    null, null,
                    240, 120, 3),
            new ProgSeed(25, 0, "Grand Finale: Fireworks & Closing Concert",
                    "An unforgettable close to a day of celebration.",
                    null, null,
                    420, 60, 4),

            // Cultural Food Festival (Chidinma, event 1)
            new ProgSeed(25, 1, "Cooking Demonstration: Dishes from Every State",
                    "Celebrity chefs prepare signature dishes from all 36 Nigerian states.",
                    "Chef Zara Abubakar", "Head Chef & Culinary Ambassador.",
                    0, 90, 1),
            new ProgSeed(25, 1, "Food Photography Masterclass",
                    "How to capture food beautifully for social media and menus.",
                    "Tobi Taiwo", "Food Photographer.",
                    100, 60, 2),

            // Annual Wellness Retreat (Daniel, event 0)
            new ProgSeed(26, 0, "Arrival, Check-In & Welcome Circle",
                    "Grounding session to transition from city pace to retreat mode.",
                    "Daniel Ibekwe", "Retreat Facilitator.",
                    0, 45, 1),
            new ProgSeed(26, 0, "Morning: Nature Walk & Silent Reflection",
                    "Guided 2-hour walk through nature with optional journaling.",
                    null, null,
                    55, 120, 2),
            new ProgSeed(26, 0, "Workshop: Digital Detox & Reconnecting with Self",
                    "Guided exercises for intentional disconnection and presence.",
                    "Dr. Ngozi Fashakin", "Mindfulness Therapist.",
                    195, 75, 3),

            // Mindfulness & Meditation Workshop (Daniel, event 1)
            new ProgSeed(26, 1, "Introduction to Mindfulness",
                    "The science and practice of mindfulness for modern professionals.",
                    "Daniel Ibekwe", "Certified MBSR Instructor.",
                    0, 60, 1),
            new ProgSeed(26, 1, "Guided Meditation Series",
                    "Body scan, breath awareness, and loving-kindness meditation.",
                    null, null,
                    70, 90, 2),
            new ProgSeed(26, 1, "Mindful Communication Workshop",
                    "Applying mindfulness to listening, speaking, and relationships.",
                    "Amaka Osei", "Communication Coach.",
                    170, 75, 3),

            // Annual Charity Gala Dinner (Ebube, event 0)
            new ProgSeed(27, 0, "Cocktail Reception",
                    "Welcome drinks and canapes as guests arrive.",
                    null, null,
                    -30, 30, 1),
            new ProgSeed(27, 0, "Opening Remarks & Charity Spotlight",
                    "Hear directly from the beneficiaries of this year's charity.",
                    "Ebube Eze", "Gala Host & Philanthropist.",
                    0, 20, 2),
            new ProgSeed(27, 0, "Three-Course Dinner",
                    "Fine dining prepared by Executive Chef.",
                    null, null,
                    25, 90, 3),
            new ProgSeed(27, 0, "Keynote: The Business Case for Giving",
                    "Why CSR and philanthropy drive long-term business performance.",
                    "Tony Elumelu", "Chairman, United Bank for Africa.",
                    120, 40, 4),
            new ProgSeed(27, 0, "Charity Auction & Fund-Raising",
                    "Live auction of art, experiences, and exclusive packages.",
                    null, null,
                    165, 75, 5),

            // Awards & Recognition Gala (Ebube, event 1)
            new ProgSeed(27, 1, "Red Carpet Arrival",
                    "Guests arrive and are photographed on the red carpet.",
                    null, null,
                    -30, 30, 1),
            new ProgSeed(27, 1, "Opening Performance",
                    "A spectacular musical performance opens the awards ceremony.",
                    null, null,
                    0, 20, 2),
            new ProgSeed(27, 1, "Awards Ceremony: Industry Excellence",
                    "20 awards across categories including Innovation, Leadership, and Impact.",
                    null, null,
                    25, 150, 3),

            // Annual Research Symposium (Folake, event 0)
            new ProgSeed(28, 0, "Opening Plenary: Frontiers of Nigerian Research",
                    "A survey of breakthrough research coming out of Nigerian institutions.",
                    "Prof. Folake Adekunle", "Dean of Research, UNILAG.",
                    0, 50, 1),
            new ProgSeed(28, 0, "Paper Presentations: Track A — STEM",
                    "Six 15-minute research paper presentations followed by Q&A.",
                    null, null,
                    60, 120, 2),
            new ProgSeed(28, 0, "Paper Presentations: Track B — Social Sciences",
                    "Six 15-minute research paper presentations followed by Q&A.",
                    null, null,
                    60, 120, 3),
            new ProgSeed(28, 0, "Poster Session & Exhibition",
                    "Researchers present findings at their poster displays.",
                    null, null,
                    200, 90, 4),
            new ProgSeed(28, 0, "Closing: Best Paper Awards",
                    "Prize giving for the top papers across all tracks.",
                    null, null,
                    310, 45, 5),

            // Academic Excellence Forum (Folake, event 1)
            new ProgSeed(28, 1, "Keynote: What Does Excellence Look Like in Nigeria?",
                    "Redefining academic success in the African context.",
                    "Dr. Ngozi Okonkwo", "Vice-Chancellor, Covenant University.",
                    0, 50, 1),
            new ProgSeed(28, 1, "Student Showcase: Most Impactful Research Projects",
                    "Five student teams present their research to the audience.",
                    null, null,
                    60, 90, 2),

            // Annual Street Carnival (Gbemi, event 0)
            new ProgSeed(29, 0, "Carnival Procession Opens",
                    "Floats, masquerades, and performers parade through the streets.",
                    "Gbemi Kolade", "Carnival Director.",
                    0, 60, 1),
            new ProgSeed(29, 0, "Main Stage: DJ Sets & Live Music",
                    "Non-stop music from Nigeria's hottest DJs.",
                    null, null,
                    60, 180, 2),
            new ProgSeed(29, 0, "Carnival Games & Activities Zone",
                    "Competitions, prizes, and interactive fun for all ages.",
                    null, null,
                    60, 300, 3),
            new ProgSeed(29, 0, "Evening Headline Concert",
                    "Three headline acts close out the carnival.",
                    null, null,
                    360, 120, 4),

            // Entertainment & Comedy Fest (Gbemi, event 1)
            new ProgSeed(29, 1, "Opening: Warm-Up Comedy Sets",
                    "Emerging comedians get 10 minutes each to warm up the crowd.",
                    null, null,
                    0, 60, 1),
            new ProgSeed(29, 1, "Headline Comedy Show",
                    "Nigeria's top three comedians perform back-to-back.",
                    "MC Basketmouth", "Nigeria's biggest stand-up comedian.",
                    70, 120, 2),
            new ProgSeed(29, 1, "Music & Comedy Fusion Finale",
                    "Live music and comedy sketches close the show.",
                    null, null,
                    200, 60, 3),

            // Annual Excellence Awards (Habiba, event 0)
            new ProgSeed(30, 0, "Welcome & Opening Remarks",
                    "Setting the tone for an evening of recognition and celebration.",
                    "Habiba Musa", "Awards Chair.",
                    0, 20, 1),
            new ProgSeed(30, 0, "Category Awards: Innovation & Technology",
                    "Recognising organisations driving innovation across Nigeria.",
                    null, null,
                    25, 45, 2),
            new ProgSeed(30, 0, "Category Awards: Social Impact & Sustainability",
                    "Honouring those making a difference beyond profit.",
                    null, null,
                    75, 45, 3),
            new ProgSeed(30, 0, "Keynote: The Responsibility of Excellence",
                    "A challenge to awardees to use their recognition to inspire others.",
                    "Prof. Wole Soyinka", "Nobel Laureate.",
                    130, 30, 4),
            new ProgSeed(30, 0, "Gala Dinner & Networking",
                    "Celebratory dinner following the awards ceremony.",
                    null, null,
                    175, 90, 5),

            // Industry Innovation Awards (Habiba, event 1)
            new ProgSeed(30, 1, "Finalists Showcase",
                    "Each finalist presents their innovation in 3 minutes.",
                    null, null,
                    0, 90, 1),
            new ProgSeed(30, 1, "Judges' Deliberation & Networking Break",
                    "Attendees network while judges deliberate.",
                    null, null,
                    100, 30, 2),
            new ProgSeed(30, 1, "Award Announcement & Keynote",
                    "Winners announced and a keynote address by the guest speaker.",
                    "Dr. Adesola Adeduntan", "CEO, First Bank.",
                    135, 60, 3),

            // Product Launch Showcase (Ibrahim, event 0)
            new ProgSeed(31, 0, "Opening: Why Nigeria Needs Local Innovation",
                    "The case for Nigerian-made products in a post-import-dependent economy.",
                    "Ibrahim Ahmed", "Founder, MadeInNigeria Initiative.",
                    0, 30, 1),
            new ProgSeed(31, 0, "Product Demonstrations: Consumer Tech",
                    "Six consumer technology products launch with live demos.",
                    null, null,
                    40, 90, 2),
            new ProgSeed(31, 0, "Product Demonstrations: FMCG & Lifestyle",
                    "Eight FMCG and lifestyle brands unveil new products.",
                    null, null,
                    140, 90, 3),
            new ProgSeed(31, 0, "Investor & Buyer Matching Session",
                    "Connect with investors and bulk buyers in structured 10-minute meetings.",
                    null, null,
                    240, 90, 4),
            new ProgSeed(31, 0, "Best New Product Awards",
                    "Audience votes for their favourite product across four categories.",
                    null, null,
                    345, 45, 5),

            // Live Demo & Presentation Event (Ibrahim, event 1)
            new ProgSeed(31, 1, "The Art of the Demo",
                    "How to demonstrate your product in a way that converts.",
                    "Tunde Fadahunsi", "Sales Coach.",
                    0, 45, 1),
            new ProgSeed(31, 1, "Hands-On Demo Stations",
                    "Attendees interact directly with products at 10 demo stations.",
                    null, null,
                    55, 90, 2),
            new ProgSeed(31, 1, "Fireside Chat: From Demo to ₦1B Revenue",
                    "How a great product demo launched a business.",
                    "Seun Osewa", "Founder, Nairaland.",
                    155, 60, 3)
    );

    // ─── guest seed data ──────────────────────────────────────────────────────
    //
    // Only for events where guestListEnabled = true (all priv() events).
    // (organiserIndex, eventIndex)

    private record GuestSeed(
            int organiserIndex, int eventIndex,
            String name, String email, String phone,
            RsvpStatus rsvpStatus, String note) {}

    private static final List<GuestSeed> GUEST_SEEDS = List.of(

            // Blockchain Developers Bootcamp — Akin, event 2 (private)
            new GuestSeed(0, 2, "Tunde Adeyemi",     "tunde.adeyemi@techmail.com",   "+2348012345670", RsvpStatus.ACCEPTED,  "CTO at FinTech startup, strong Solidity background."),
            new GuestSeed(0, 2, "Nkechi Okonkwo",    "nkechi.okonkwo@devmail.com",   "+2348023456781", RsvpStatus.ACCEPTED,  "Smart contract developer, 3 years experience."),
            new GuestSeed(0, 2, "Babatunde Lawal",   "b.lawal@blockchaindev.ng",     "+2348034567892", RsvpStatus.PENDING,   null),
            new GuestSeed(0, 2, "Amaka Eze",         "amaka.eze@cryptoafrica.com",   "+2348045678903", RsvpStatus.PENDING,   null),
            new GuestSeed(0, 2, "Segun Alabi",       "segun.alabi@web3hub.ng",       "+2348056789014", RsvpStatus.DECLINED,  "Scheduling conflict — requested a recording."),

            // Gospel Concert 2026 — Chioma, event 3 (private)
            new GuestSeed(1, 3, "Pastor Emeka Nwosu","pastor.nwosu@gracechurch.ng",  "+2348067890125", RsvpStatus.ACCEPTED,  "Senior Pastor, Grace Community Church."),
            new GuestSeed(1, 3, "Blessing Adeyemi",  "blessing.a@gloryhaven.com",    "+2348078901236", RsvpStatus.ACCEPTED,  "Choir director, 15-voice ensemble attending."),
            new GuestSeed(1, 3, "Joseph Okafor",     "j.okafor@faithministries.ng",  "+2348089012347", RsvpStatus.ACCEPTED,  null),
            new GuestSeed(1, 3, "Miriam Abubakar",   "miriam.abubakar@frcn.gov.ng",  "+2348090123458", RsvpStatus.PENDING,   "Media rep from Federal Radio Corporation."),
            new GuestSeed(1, 3, "Grace Olawale",     "grace.olawale@gmail.com",      "+2348001234569", RsvpStatus.DECLINED,  "Travelling — will attend the next edition."),
            new GuestSeed(1, 3, "Deacon Femi Ojo",   "deacon.ojo@rccglagos.org",     "+2348012345670", RsvpStatus.PENDING,   null),

            // SME Growth Conference — Emeka, event 1 (private)
            new GuestSeed(2, 1, "Sola Adewale",      "sola.adewale@smeconnect.ng",   "+2348023456781", RsvpStatus.ACCEPTED,  "Owner, Adewale Bakeries — 3 outlets in Lagos."),
            new GuestSeed(2, 1, "Kemi Babatunde",    "kemi.b@fashionstudio.ng",      "+2348034567892", RsvpStatus.ACCEPTED,  "Fashion designer, annual revenue ₦12M."),
            new GuestSeed(2, 1, "Rasheed Yusuf",     "rasheed.yusuf@logistics247.ng","+2348045678903", RsvpStatus.ACCEPTED,  "Founder, Last-Mile Logistics startup."),
            new GuestSeed(2, 1, "Ngozi Dike",        "ngozi.dike@agroplus.ng",       "+2348056789014", RsvpStatus.PENDING,   "AgriTech founder — interested in the finance session."),
            new GuestSeed(2, 1, "Chukwuma Obi",      "c.obi@buildmaster.ng",         "+2348067890125", RsvpStatus.PENDING,   null),
            new GuestSeed(2, 1, "Yetunde Ajayi",     "yetunde.ajayi@retailng.com",   "+2348078901236", RsvpStatus.DECLINED,  "Conflict with another engagement same day."),

            // Culinary Arts Masterclass — Funke, event 3 (private)
            new GuestSeed(3, 3, "Adaeze Nwosu",      "adaeze.nwosu@chef.ng",         "+2348089012347", RsvpStatus.ACCEPTED,  "Professional chef — interested in the pastry session."),
            new GuestSeed(3, 3, "Femi Coker",        "femi.coker@lagoskitchen.ng",   "+2348090123458", RsvpStatus.ACCEPTED,  "Restaurant owner, Lekki Phase 1."),
            new GuestSeed(3, 3, "Bisi Oladele",      "bisi.oladele@homecooks.ng",    "+2348001234569", RsvpStatus.ACCEPTED,  null),
            new GuestSeed(3, 3, "Tobi Adeyinka",     "tobi.a@culinaryschool.ng",     "+2348012345670", RsvpStatus.PENDING,   "Culinary student, final year UNILAG."),
            new GuestSeed(3, 3, "Chiamaka Osei",     "chiamaka.osei@foodblog.ng",    "+2348023456781", RsvpStatus.PENDING,   "Food blogger, 45k Instagram followers."),

            // Swimming Championship — Gbenga, event 3 (private)
            new GuestSeed(4, 3, "Coach Seun Badmus", "s.badmus@swimng.org",           "+2348034567892", RsvpStatus.ACCEPTED,  "Head coach, Nigerian Swimming Federation."),
            new GuestSeed(4, 3, "Afolabi Martins",   "afolabi.m@swimmersclub.ng",    "+2348045678903", RsvpStatus.ACCEPTED,  "U-18 national champion — competing in 100m freestyle."),
            new GuestSeed(4, 3, "Nneka Okoye",       "nneka.okoye@usports.ng",       "+2348056789014", RsvpStatus.ACCEPTED,  "University sports liaison officer."),
            new GuestSeed(4, 3, "Dr. Bayo Ogunleye", "b.ogunleye@sportsmedicine.ng", "+2348067890125", RsvpStatus.PENDING,   "Sports medicine physician — medical cover."),

            // Nollywood Film Premiere — Ifeanyi, event 1 (private)
            new GuestSeed(6, 1, "Funke Akindele",    "funke.akindele@nollywood.ng",  "+2348078901236", RsvpStatus.ACCEPTED,  "A-list actress — confirmed attendance."),
            new GuestSeed(6, 1, "Genevieve Nnaji",   "g.nnaji@nollywoodstudios.ng",  "+2348089012347", RsvpStatus.ACCEPTED,  "Actress and producer."),
            new GuestSeed(6, 1, "Richard Mofe-Damijo","rmd@nollywood.ng",             "+2348090123458", RsvpStatus.ACCEPTED,  "Veteran actor — confirmed."),
            new GuestSeed(6, 1, "Linda Ikeji",       "press@lindaikeji.ng",           "+2348001234569", RsvpStatus.ACCEPTED,  "Entertainment journalist — front-row press seat."),
            new GuestSeed(6, 1, "Iyabo Ojo",         "iyabo.ojo@films.ng",            "+2348012345670", RsvpStatus.PENDING,   null),
            new GuestSeed(6, 1, "Toyin Abraham",     "toyin.abraham@nollywoodng.com", "+2348023456781", RsvpStatus.PENDING,   null),
            new GuestSeed(6, 1, "Ramsey Nouah",      "ramsey.nouah@classicfilms.ng", "+2348034567892", RsvpStatus.DECLINED,  "Filming in Abuja — sends congratulations."),

            // Yoga & Mindfulness Retreat — Jumoke, event 2 (private/free)
            new GuestSeed(7, 2, "Adunola Bello",     "adunola.b@wellnessng.com",     "+2348045678903", RsvpStatus.ACCEPTED,  "Certified yoga instructor — assisting with sessions."),
            new GuestSeed(7, 2, "Taiwo Adesanya",    "taiwo.adesanya@mindful.ng",    "+2348056789014", RsvpStatus.ACCEPTED,  "Mindfulness coach."),
            new GuestSeed(7, 2, "Chidinma Obi",      "chidinma.obi@healthcoach.ng",  "+2348067890125", RsvpStatus.ACCEPTED,  null),
            new GuestSeed(7, 2, "Habiba Musa",       "habiba.musa@wellness.ng",      "+2348078901236", RsvpStatus.PENDING,   "First-time attendee — has knee injury, needs modifications."),
            new GuestSeed(7, 2, "Kelechi Eze",       "kelechi.eze@gmail.com",        "+2348089012347", RsvpStatus.PENDING,   null),

            // Real Estate Investment Forum — Kola, event 1 (private)
            new GuestSeed(8, 1, "Alhaji Musa Ibrahim","alhaji.musa@propertiesng.com", "+2348090123458", RsvpStatus.ACCEPTED,  "Property developer — portfolio value ₦2B+."),
            new GuestSeed(8, 1, "Mrs. Bisi Okonkwo", "bisi.okonkwo@realestateng.ng", "+2348001234569", RsvpStatus.ACCEPTED,  "CEO, Okonkwo Properties."),
            new GuestSeed(8, 1, "Engr. Rotimi Adeyemo","r.adeyemo@buildcorp.ng",     "+2348012345670", RsvpStatus.ACCEPTED,  "Civil engineer and real estate investor."),
            new GuestSeed(8, 1, "Dr. Chuka Nwosu",   "c.nwosu@mortgageng.com",       "+2348023456781", RsvpStatus.ACCEPTED,  "MD, Federal Mortgage Bank of Nigeria."),
            new GuestSeed(8, 1, "Simi Lawal",        "simi.lawal@assetmgt.ng",       "+2348034567892", RsvpStatus.PENDING,   "Asset management firm — exploring real estate allocation."),
            new GuestSeed(8, 1, "Gbenga Coker",      "gbenga.coker@invest.ng",       "+2348045678903", RsvpStatus.PENDING,   null),
            new GuestSeed(8, 1, "Yemi Osinbajo Jr.", "yemi.jr@capgenius.ng",         "+2348056789014", RsvpStatus.DECLINED,  "Conflict — requested speaker slides be shared."),

            // Fabric & Textile Market — Lara, event 2 (private/free)
            new GuestSeed(9, 2, "Damilola Adeyemi",  "dami.adeyemi@textilebuyers.ng","+2348067890125", RsvpStatus.ACCEPTED,  "Bulk fabric buyer — sources for 12 boutiques."),
            new GuestSeed(9, 2, "Chisom Eze",        "chisom.eze@designer.ng",       "+2348078901236", RsvpStatus.ACCEPTED,  "Fashion designer, Yaba."),
            new GuestSeed(9, 2, "Aisha Suleiman",    "aisha.s@fabricmarket.ng",      "+2348089012347", RsvpStatus.ACCEPTED,  "Textile trader, Balogun Market."),
            new GuestSeed(9, 2, "Tola Bakare",       "tola.bakare@sewingschool.ng",  "+2348090123458", RsvpStatus.PENDING,   "Sewing school owner — interested in Ankara suppliers."),
            new GuestSeed(9, 2, "Kemi Oluwafemi",    "kemi.o@fashionweek.ng",        "+2348001234569", RsvpStatus.PENDING,   "Lagos Fashion Week coordinator.")
    );

    // ─── vendor application seed data ─────────────────────────────────────────
    //
    // (organiserIndex, eventIndex) references the EVENTS_PER_USER table above.
    // eventIndex 0 and 1 are always PUBLISHED — safe for vendor applications.
    // applicantIndex references the USERS array.

    private record VendorSeed(
            int organiserIndex, int eventIndex,
            int applicantIndex,
            String serviceType, String description,
            BigDecimal proposedAmount,
            VendorApplicationStatus status) {}

    private static final List<VendorSeed> VENDOR_SEEDS = List.of(

            // ── ACCEPTED ──────────────────────────────────────────────────────
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

            // ── PENDING ───────────────────────────────────────────────────────
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

            // ── REJECTED ──────────────────────────────────────────────────────
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

    // ─── runner ───────────────────────────────────────────────────────────────

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(SEED_CHECK_EMAIL)) {
            log.info("Dev seed users present — checking for missing tiers");
            backfillMissingTiers();
            return;
        }

        List<User> users = seedUsers();
        List<List<Events>> eventsByUser = seedEvents(users);
        List<VendorApplication> vendorApps = seedVendorApplications(users, eventsByUser);
        seedProgramme(eventsByUser);
        seedGuests(eventsByUser);
        Map<VendorApplication, Conversation> contractConversations = seedConversations(users, eventsByUser, vendorApps);
        seedMessages(contractConversations);
        seedContracts(vendorApps, contractConversations);

        log.info("Dev seed complete — {} users ({} verified vendors), {} events, {} vendor apps, {} conversations, {} contracts/escrow with milestones",
                users.size(), VERIFIED_VENDORS.size(), users.size() * 5, vendorApps.size(),
                contractConversations.size(), contractConversations.size());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private List<User> seedUsers() {
        // Build a lookup: userIndex → verified vendor profile
        java.util.Map<Integer, VerifiedVendorProfile> verifiedMap = new java.util.HashMap<>();
        for (VerifiedVendorProfile vvp : VERIFIED_VENDORS) {
            verifiedMap.put(vvp.userIndex(), vvp);
        }

        List<User> saved = new ArrayList<>();
        for (int i = 0; i < USERS.length; i++) {
            String[] d = USERS[i];
            VerifiedVendorProfile vvp = verifiedMap.get(i);

            User.UserBuilder builder = User.builder()
                    .firstName(d[0]).lastName(d[1]).email(d[2])
                    .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                    .role(Role.USER).enabled(true);

            if (vvp != null) {
                builder
                        .vendorVerified(true)
                        .vendorVerificationStatus(VendorVerificationStatus.VERIFIED)
                        .vendorServiceType(vvp.serviceType())
                        .vendorProfileDescription(vvp.bio())
                        .vendorVerificationSubmittedAt(LocalDateTime.now().minusDays(30))
                        .vendorVerifiedAt(LocalDateTime.now().minusDays(25));
            }

            saved.add(userRepository.save(builder.build()));
            log.debug("Seeded user: {} {} (vendor={})", d[0], d[1], vvp != null);
        }
        return saved;
    }

    /** Seeds all 50 events and returns them grouped by user (index matches USERS). */
    private List<List<Events>> seedEvents(List<User> users) {
        List<List<Events>> result = new ArrayList<>();
        int venueIndex = 0;

        for (int i = 0; i < users.size(); i++) {
            User organizer  = users.get(i);
            List<TierSeed> tierSeeds = TIERS_PER_THEME.get(i);
            List<Events>   userEvents = new ArrayList<>();

            for (EventSeed seed : EVENTS_PER_USER.get(i)) {
                LocalDateTime start = LocalDateTime.now()
                        .withHour(9).withMinute(0).withSecond(0).withNano(0)
                        .plusDays(seed.startDaysFromNow());
                LocalDateTime end = start.plusHours(seed.durationHours());

                Events saved = eventRepository.save(Events.builder()
                        .title(seed.title())
                        .description(seed.description())
                        .venue(VENUES[venueIndex % VENUES.length])
                        .startTime(start)
                        .endTime(end)
                        .checkInStartTime(start.minusHours(2))
                        .status(seed.status())
                        .visibility(seed.visibility())
                        .createdBy(organizer)
                        .build());

                userEvents.add(saved);

                // Create event config — programme on by default; guest list only for private events
                eventConfigRepository.save(EventConfig.builder()
                        .event(saved)
                        .ticketingEnabled(true)
                        .guestListEnabled(seed.guestListEnabled())
                        .programmeEnabled(true)
                        .ratingsEnabled(false)
                        .build());

                List<TierSeed> tiersToSave = seed.free()
                        ? List.of(new TierSeed("General", BigDecimal.ZERO, "G", 50, 10))
                        : tierSeeds;

                tiersToSave.forEach(t -> {
                    int cap = t.rowCount() * t.seatsPerRow();
                    tierRepository.save(TicketTier.builder()
                            .event(saved).name(t.name()).price(t.price())
                            .rowPrefix(t.rowPrefix()).rowCount(t.rowCount())
                            .seatsPerRow(t.seatsPerRow())
                            .totalCapacity(cap).availableCapacity(cap)
                            .build());
                });

                membershipRepository.save(EventMembership.builder()
                        .user(organizer).events(saved)
                        .role(EventRole.ORGANIZER).status(MembershipStatus.ACTIVE)
                        .assignedBy(organizer)
                        .build());

                venueIndex++;
                log.debug("Seeded event: [{}] {} ({})", seed.status(), seed.title(), seed.free() ? "FREE" : "PAID");
            }

            result.add(userEvents);
        }

        log.info("Seeded 50 events across {} organizers", users.size());
        return result;
    }

    private List<VendorApplication> seedVendorApplications(List<User> users, List<List<Events>> eventsByUser) {
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

    private void seedProgramme(List<List<Events>> eventsByUser) {
        for (ProgSeed ps : PROGRAMME_SEEDS) {
            Events event = eventsByUser.get(ps.organiserIndex()).get(ps.eventIndex());
            LocalDateTime start = event.getStartTime().plusMinutes(ps.offsetMinutes());
            LocalDateTime end   = start.plusMinutes(ps.durationMinutes());
            programmeItemRepository.save(ProgrammeItem.builder()
                    .event(event)
                    .title(ps.title())
                    .description(ps.description())
                    .speakerName(ps.speakerName())
                    .speakerBio(ps.speakerBio())
                    .startTime(start)
                    .endTime(end)
                    .displayOrder(ps.displayOrder())
                    .build());
            log.debug("Seeded programme item: [{}] {}", event.getTitle(), ps.title());
        }
        log.info("Seeded {} programme items", PROGRAMME_SEEDS.size());
    }

    private void seedGuests(List<List<Events>> eventsByUser) {
        int seededCount = 0;
        for (GuestSeed gs : GUEST_SEEDS) {
            Events event = eventsByUser.get(gs.organiserIndex()).get(gs.eventIndex());
            // Only add guests to published events with guest list enabled
            if (event.getStatus() != EventStatus.PUBLISHED) {
                log.debug("Skipping guest: {} → {} (event not published)", gs.name(), event.getTitle());
                continue;
            }
            guestRepository.save(Guest.builder()
                    .event(event)
                    .name(gs.name())
                    .email(gs.email())
                    .phone(gs.phone())
                    .rsvpStatus(gs.rsvpStatus())
                    .note(gs.note())
                    .invitedAt(LocalDateTime.now().minusDays(7))
                    .respondedAt(gs.rsvpStatus() != RsvpStatus.PENDING
                            ? LocalDateTime.now().minusDays(3) : null)
                    .build());
            log.debug("Seeded guest: {} → {}", gs.name(), event.getTitle());
            seededCount++;
        }
        log.info("Seeded {} guests across {} private events", seededCount, VERIFIED_VENDORS.size());
    }

    private Map<VendorApplication, Conversation> seedConversations(List<User> users, List<List<Events>> eventsByUser, List<VendorApplication> vendorApps) {
        Map<VendorApplication, Conversation> conversations = new java.util.HashMap<>();

        for (VendorApplication app : vendorApps) {
            User organizer = app.getEvent().getCreatedBy();
            User vendor = app.getApplicant();

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

    private void seedMessages(Map<VendorApplication, Conversation> conversations) {
        String[] organizerMessages = {
                "Hi, interested in your services for our event.",
                "Can you share your availability and timeline?",
                "What's your experience with events of this size?",
                "Let's discuss the details and finalize the contract.",
                "Excellent! We're excited to work with you."
        };

        String[] vendorMessages = {
                "Thanks for reaching out! Happy to help with your event.",
                "We're available during that period. Here are our rates.",
                "We've handled 50+ similar events. Check our portfolio.",
                "Agreed. When can we meet to sign the contract?",
                "Perfect! Looking forward to delivering quality service."
        };

        int messageIndex = 0;
        for (Map.Entry<VendorApplication, Conversation> entry : conversations.entrySet()) {
            Conversation conv = entry.getValue();
            VendorApplication app = entry.getKey();
            User organizer = app.getEvent().getCreatedBy();
            User vendor = app.getApplicant();

            // Alternate between organizer and vendor messages (5-7 messages per conversation)
            int msgCount = 5 + (messageIndex % 3);
            for (int i = 0; i < msgCount; i++) {
                User sender = (i % 2 == 0) ? organizer : vendor;
                String content = (i % 2 == 0) ? organizerMessages[i % organizerMessages.length]
                        : vendorMessages[i % vendorMessages.length];

                messageRepository.save(Message.builder()
                        .conversation(conv).sender(sender).content(content)
                        .build());
            }
            messageIndex++;
            log.debug("Seeded {} messages in conversation with {}", msgCount, vendor.getEmail());
        }
    }

    private void seedContracts(List<VendorApplication> vendorApps, Map<VendorApplication, Conversation> conversations) {
        for (VendorApplication app : vendorApps) {
            // Only create contracts for ACCEPTED applications
            if (app.getStatus() != VendorApplicationStatus.ACCEPTED) {
                continue;
            }

            VendorContract contract = vendorContractRepository.save(VendorContract.builder()
                    .event(app.getEvent())
                    .organizer(app.getEvent().getCreatedBy())
                    .vendor(app.getApplicant())
                    .vendorApplication(app)
                    .conversation(conversations.get(app))
                    .title(app.getServiceType() + " Services - " + app.getEvent().getTitle())
                    .description("Contract for providing " + app.getServiceType() + " services for event: " + app.getEvent().getTitle())
                    .terms("Payment terms: 50% upfront, 50% on completion. Service quality as per industry standards.")
                    .amount(app.getProposedAmount())
                    .status(ContractStatus.SIGNED)
                    .signedAt(LocalDateTime.now().minusDays(5))
                    .fundedAt(LocalDateTime.now().minusDays(3))
                    .activatedAt(LocalDateTime.now().minusDays(2))
                    .build());

            // Create escrow account with milestones
            List<EscrowMilestone> milestones = new ArrayList<>();
            BigDecimal amount = app.getProposedAmount();
            int numMilestones = 3;
            BigDecimal perMilestone = amount.divide(new BigDecimal(numMilestones));

            EscrowAccount escrow = escrowAccountRepository.save(EscrowAccount.builder()
                    .contract(contract)
                    .totalAmount(amount)
                    .releasedAmount(perMilestone) // First milestone approved
                    .status(EscrowStatus.FUNDED)
                    .fundedAt(LocalDateTime.now().minusDays(3))
                    .build());

            // Create milestone stages
            for (int i = 1; i <= numMilestones; i++) {
                EscrowMilestone milestone = escrowMilestoneRepository.save(EscrowMilestone.builder()
                        .escrow(escrow)
                        .title("Stage " + i + ": " + getMilestoneTitle(i, numMilestones))
                        .description(getMilestoneDescription(i, numMilestones))
                        .amount(perMilestone)
                        .status(i == 1 ? MilestoneStatus.RELEASED : (i == 2 ? MilestoneStatus.APPROVED : MilestoneStatus.PENDING))
                        .displayOrder(i)
                        .approvedAt(i <= 2 ? LocalDateTime.now().minusDays(5 - i) : null)
                        .releasedAt(i == 1 ? LocalDateTime.now().minusDays(2) : null)
                        .build());
                milestones.add(milestone);
            }
            escrow.setMilestones(milestones);
            escrowAccountRepository.save(escrow);

            log.debug("Seeded contract with escrow & milestones: {} (amount: ₦{})", app.getServiceType(), amount);
        }
    }

    private String getMilestoneTitle(int index, int total) {
        if (total == 3) {
            return index == 1 ? "Initial Deposit (Released)" : (index == 2 ? "Partial Progress (Approved)" : "Final Delivery (Pending)");
        }
        return "Milestone " + index;
    }

    private String getMilestoneDescription(int index, int total) {
        if (total == 3) {
            return index == 1 ? "Initial payment released to kickstart project"
                    : (index == 2 ? "Payment approved upon significant progress" : "Final payment pending upon project completion");
        }
        return "Milestone " + index + " of " + total;
    }

    private void backfillMissingTiers() {
        int filled = 0;
        for (int i = 0; i < USERS.length; i++) {
            final int idx = i;
            userRepository.findByEmail(USERS[i][2]).ifPresent(user -> {
                List<TierSeed> themeTiers = TIERS_PER_THEME.get(idx);
                for (Events event : eventRepository.findAllByOrganizerId(user.getId())) {
                    if (!tierRepository.findAllByEventId(event.getId()).isEmpty()) continue;

                    boolean isFree = event.getDescription() != null
                            && event.getDescription().startsWith("Free");
                    List<TierSeed> toAdd = isFree
                            ? List.of(new TierSeed("General", BigDecimal.ZERO, "G", 50, 10))
                            : themeTiers;

                    toAdd.forEach(t -> {
                        int cap = t.rowCount() * t.seatsPerRow();
                        tierRepository.save(TicketTier.builder()
                                .event(event).name(t.name()).price(t.price())
                                .rowPrefix(t.rowPrefix()).rowCount(t.rowCount())
                                .seatsPerRow(t.seatsPerRow())
                                .totalCapacity(cap).availableCapacity(cap)
                                .build());
                    });
                    log.debug("Backfilled tiers for: {}", event.getTitle());
                }
            });
            filled++;
        }
        log.info("Tier backfill complete for {} organizers", filled);
    }
}
