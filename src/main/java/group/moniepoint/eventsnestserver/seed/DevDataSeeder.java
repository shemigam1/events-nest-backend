package group.moniepoint.eventsnestserver.seed;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.model.VendorVerificationStatus;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.events.models.*;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
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
    private final EventMembershipRepository   membershipRepository;
    private final TicketTierRepository        tierRepository;
    private final VendorApplicationRepository vendorApplicationRepository;
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
            EventStatus status, boolean free) {}

    private static final List<List<EventSeed>> EVENTS_PER_USER = List.of(

            // Akin — Tech
            List.of(
                    new EventSeed("TechFest Lagos 2026",            "The biggest tech festival in West Africa.",              45,  8, EventStatus.PUBLISHED,        false),
                    new EventSeed("AI & Machine Learning Summit",    "Exploring cutting-edge AI applications.",                20,  6, EventStatus.PUBLISHED,        false),
                    new EventSeed("Blockchain Developers Bootcamp",  "Hands-on blockchain development training.",              60,  8, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Startup Pitch Night",             "Free — early-stage founders pitch to investors.",        30,  4, EventStatus.DRAFT,            true),
                    new EventSeed("Digital Innovation Conference",   "Past conference on digital transformation.",            -20,  6, EventStatus.PUBLISHED,        false)
            ),

            // Chioma — Music & Arts
            List.of(
                    new EventSeed("Afrobeats Night Out",             "Live Afrobeats performances by top artists.",            15,  5, EventStatus.PUBLISHED,        false),
                    new EventSeed("Lagos Jazz Festival",             "Three days of world-class jazz.",                        90,  6, EventStatus.PUBLISHED,        false),
                    new EventSeed("Open Mic Wednesday",              "Free — discover emerging spoken word and music talent.",  7,  3, EventStatus.DRAFT,            true),
                    new EventSeed("Gospel Concert 2026",             "An uplifting evening of gospel music.",                  50,  4, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Highlife Revival Night",          "Celebrating the golden era of highlife music.",         -30,  5, EventStatus.PUBLISHED,        false)
            ),

            // Emeka — Business
            List.of(
                    new EventSeed("Entrepreneurs Summit 2026",       "Connecting founders, mentors and investors.",            35,  8, EventStatus.PUBLISHED,        false),
                    new EventSeed("SME Growth Conference",           "Practical strategies for small business growth.",        55,  6, EventStatus.PUBLISHED,        false),
                    new EventSeed("Investment & Finance Forum",      "Understanding the Nigerian capital markets.",             40,  5, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Business Networking Brunch",      "Free — curated networking for professionals.",           10,  3, EventStatus.DRAFT,            true),
                    new EventSeed("Export Trade Fair",               "Cancelled due to venue unavailability.",                 25,  8, EventStatus.CANCELLED,        false)
            ),

            // Funke — Food & Lifestyle
            List.of(
                    new EventSeed("Lagos Food & Wine Festival",      "Celebrating Nigeria's rich culinary culture.",           28,  8, EventStatus.PUBLISHED,        false),
                    new EventSeed("Healthy Living Expo",             "Wellness, nutrition and fitness under one roof.",        70,  6, EventStatus.PUBLISHED,        false),
                    new EventSeed("Street Food Carnival",            "Free — a tour of Lagos's best street food vendors.",     14,  5, EventStatus.DRAFT,            true),
                    new EventSeed("Culinary Arts Masterclass",       "Learn from award-winning Nigerian chefs.",               42,  4, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Farm-to-Table Dinner Experience", "An intimate dinner using locally sourced produce.",     -15,  4, EventStatus.PUBLISHED,        false)
            ),

            // Gbenga — Sports
            List.of(
                    new EventSeed("Lagos Marathon 2026",             "Annual marathon through the heart of Lagos.",           100,  8, EventStatus.PUBLISHED,        false),
                    new EventSeed("Inter-Company Football League",   "Corporate 5-a-side football tournament.",               22,  6, EventStatus.PUBLISHED,        false),
                    new EventSeed("Basketball Invitational",         "Top university basketball teams compete.",               33,  6, EventStatus.DRAFT,            false),
                    new EventSeed("Swimming Championship",           "National age-group swimming competition.",               48,  8, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Fitness & Wellness Weekend",      "Free — past weekend fitness retreat.",                  -10,  8, EventStatus.PUBLISHED,        true)
            ),

            // Halima — Education
            List.of(
                    new EventSeed("Women in STEM Conference",        "Inspiring the next generation of female engineers.",     38,  6, EventStatus.PUBLISHED,        false),
                    new EventSeed("Youth Leadership Summit",         "Empowering young leaders across Nigeria.",               65,  8, EventStatus.PUBLISHED,        false),
                    new EventSeed("Creative Writing Workshop",       "Free — develop your voice through guided writing.",      12,  4, EventStatus.DRAFT,            true),
                    new EventSeed("Science & Technology Fair",       "Showcasing student innovations and inventions.",         52,  8, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Graduate Career Expo",            "Free — cancelled, rescheduled to next quarter.",         18,  6, EventStatus.CANCELLED,        true)
            ),

            // Ifeanyi — Entertainment
            List.of(
                    new EventSeed("Comedy Night Live",               "Nigeria's funniest comedians on one stage.",              8,  3, EventStatus.PUBLISHED,        false),
                    new EventSeed("Nollywood Film Premiere",         "Exclusive premiere of an anticipated feature film.",     44,  3, EventStatus.PUBLISHED,        false),
                    new EventSeed("Stand-Up Showcase",               "Free — open showcase for emerging stand-up comedians.", 19,  3, EventStatus.DRAFT,            true),
                    new EventSeed("Fashion & Style Show",            "Runway show featuring Nigerian designers.",              36,  4, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Cultural Heritage Night",         "Free — celebrating Nigeria's diverse cultural traditions.", -25, 5, EventStatus.PUBLISHED,    true)
            ),

            // Jumoke — Health
            List.of(
                    new EventSeed("Mental Health Awareness Day",     "Free — breaking stigma and promoting mental wellness.",  26,  6, EventStatus.PUBLISHED,        true),
                    new EventSeed("Healthcare Innovation Summit",    "The future of healthcare delivery in Africa.",           80,  8, EventStatus.PUBLISHED,        false),
                    new EventSeed("Yoga & Mindfulness Retreat",      "Free — a full-day retreat focused on inner balance.",   16,  8, EventStatus.DRAFT,            true),
                    new EventSeed("Medical Research Symposium",      "Presenting breakthroughs in Nigerian healthcare.",       58,  6, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Community Health Fair",           "Free — screenings and health education for all.",       -5,  6, EventStatus.PUBLISHED,        true)
            ),

            // Kola — Real Estate
            List.of(
                    new EventSeed("Lagos Property Expo",             "The premier real estate showcase in West Africa.",       32,  8, EventStatus.PUBLISHED,        false),
                    new EventSeed("Real Estate Investment Forum",    "Strategies for building a profitable property portfolio.", 68, 6, EventStatus.PUBLISHED,       false),
                    new EventSeed("Architecture & Design Showcase",  "Free — featuring Nigeria's most innovative architects.", 23,  5, EventStatus.DRAFT,            true),
                    new EventSeed("Smart Cities Conference",         "Urban planning and technology for future cities.",       46,  6, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Housing Finance Summit",          "Cancelled — speaker scheduling conflict.",               29,  5, EventStatus.CANCELLED,        false)
            ),

            // Lara — Fashion
            List.of(
                    new EventSeed("Lagos Fashion Week",              "The continent's most prestigious fashion event.",        75,  8, EventStatus.PUBLISHED,        false),
                    new EventSeed("Designers Showcase 2026",         "Up-and-coming Nigerian designers take the spotlight.",  18,  5, EventStatus.PUBLISHED,        false),
                    new EventSeed("Fabric & Textile Market",         "Free — premium fabrics from across West Africa.",        9,  6, EventStatus.DRAFT,            true),
                    new EventSeed("Beauty & Cosmetics Expo",         "Celebrating African beauty brands and innovations.",    54,  6, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Vintage Clothing Fair",           "A curated showcase of pre-loved fashion pieces.",      -40,  5, EventStatus.PUBLISHED,        false)
            )
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

        log.info("Dev seed complete — {} users ({} platform-verified vendors), 50 events, {} vendor applications",
                users.size(), VERIFIED_VENDORS.size(), VENDOR_SEEDS.size());
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
                        .visibility(EventVisibility.PUBLIC)
                        .createdBy(organizer)
                        .build());

                userEvents.add(saved);

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
