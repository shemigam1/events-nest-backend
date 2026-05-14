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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds realistic demo data for local development and demos.
 * Only active on the "local" Spring profile — never runs in production.
 *
 * What gets created:
 *   - 10 users across 10 themed verticals
 *   - 50 events (5 per user: mix of PUBLISHED, PENDING_APPROVAL, DRAFT, CANCELLED)
 *   - Ticket tiers (General + VIP) per event; free events get a single ₦0 tier
 *   - Organiser memberships for every event
 *   - 15 vendor applications in all three states (ACCEPTED / PENDING / REJECTED)
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
    private final PasswordEncoder             passwordEncoder;

    private static final String SEED_CHECK_EMAIL = "akin@devmail.com";
    private static final String DEFAULT_PASSWORD  = "Password1!";

    // ─── user seed data ───────────────────────────────────────────────────────

    private static final String[][] USERS = {
            {"Akin",    "Ogundimu", "akin@devmail.com"},
            {"Chioma",  "Eze",      "chioma@devmail.com"},
            {"Emeka",   "Obi",      "emeka@devmail.com"},
            {"Funke",   "Adeyemi",  "funke@devmail.com"},
            {"Gbenga",  "Lawal",    "gbenga@devmail.com"},
            {"Halima",  "Musa",     "halima@devmail.com"},
            {"Ifeanyi", "Nwosu",    "ifeanyi@devmail.com"},
            {"Jumoke",  "Bello",    "jumoke@devmail.com"},
            {"Kola",    "Adebayo",  "kola@devmail.com"},
            {"Lara",    "Williams", "lara@devmail.com"},
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
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("25000"), "V", 5, 5)),  // Tech
            List.of(new TierSeed("General", new BigDecimal("8000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("30000"), "V", 5, 5)),  // Music
            List.of(new TierSeed("General", new BigDecimal("10000"), "G", 15, 10), new TierSeed("VIP", new BigDecimal("50000"), "V", 4, 5)),  // Business
            List.of(new TierSeed("General", new BigDecimal("3000"),  "G", 30, 10), new TierSeed("VIP", new BigDecimal("15000"), "V", 6, 5)),  // Food
            List.of(new TierSeed("General", new BigDecimal("2000"),  "G", 50, 10), new TierSeed("VIP", new BigDecimal("10000"), "V", 10, 5)), // Sports
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5)),  // Education
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5)),  // Entertainment
            List.of(new TierSeed("General", new BigDecimal("3000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("15000"), "V", 5, 5)),  // Health
            List.of(new TierSeed("General", new BigDecimal("10000"), "G", 10, 10), new TierSeed("VIP", new BigDecimal("50000"), "V", 4, 5)),  // Real Estate
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5))   // Fashion
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
                    195, 45, 5)
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
        seedVendorApplications(users, eventsByUser);
        seedProgramme(eventsByUser);
        seedGuests(eventsByUser);

        log.info("Dev seed complete — {} users ({} verified vendors), 50 events, {} vendor apps, {} programme items, {} guests",
                users.size(), VERIFIED_VENDORS.size(), VENDOR_SEEDS.size(),
                PROGRAMME_SEEDS.size(), GUEST_SEEDS.size());
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

    private void seedVendorApplications(List<User> users, List<List<Events>> eventsByUser) {
        User admin = userRepository.findByEmail("admin@eventsnest.com").orElse(null);

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
            vendorApplicationRepository.save(VendorApplication.builder()
                    .event(event).applicant(applicant)
                    .serviceType(vs.serviceType()).description(vs.description())
                    .proposedAmount(vs.proposedAmount()).status(vs.status())
                    .reviewedBy(reviewed ? admin : null)
                    .reviewedAt(reviewed ? LocalDateTime.now() : null)
                    .build());

            log.debug("Seeded vendor application: [{}] {} → {} ({})",
                    vs.status(), applicant.getEmail(), event.getTitle(), vs.serviceType());
        }
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
