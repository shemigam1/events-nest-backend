package group.moniepoint.eventsnestserver.seed;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventDay;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.models.MembershipStatus;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventDayRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.programme.model.ProgrammeItem;
import group.moniepoint.eventsnestserver.programme.repository.ProgrammeItemRepository;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
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

@Slf4j
@Component
@Profile("local")
@Order(10)
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final EventRespository eventRepository;
    private final EventMembershipRepository membershipRepository;
    private final TicketTierRepository tierRepository;
    private final EventConfigRepository configRepository;
    private final EventDayRepository dayRepository;
    private final ProgrammeItemRepository programmeItemRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String SEED_CHECK_EMAIL = "akin@devmail.com";
    private static final String DEFAULT_PASSWORD  = "Password1!";

    // ─── user seed data ───────────────────────────────────────────────────────────

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

    // ─── venue pool ──────────────────────────────────────────────────────────────

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

    // ─── tier seed data ───────────────────────────────────────────────────────────

    private record TierSeed(String name, BigDecimal price, String rowPrefix, int rowCount, int seatsPerRow) {}

    // Two tiers per theme: [General, VIP] — indexed by user position
    private static final List<List<TierSeed>> TIERS_PER_THEME = List.of(
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("25000"), "V", 5, 5)),   // Tech
            List.of(new TierSeed("General", new BigDecimal("8000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("30000"), "V", 5, 5)),   // Music
            List.of(new TierSeed("General", new BigDecimal("10000"), "G", 15, 10), new TierSeed("VIP", new BigDecimal("50000"), "V", 4, 5)),   // Business
            List.of(new TierSeed("General", new BigDecimal("3000"),  "G", 30, 10), new TierSeed("VIP", new BigDecimal("15000"), "V", 6, 5)),   // Food
            List.of(new TierSeed("General", new BigDecimal("2000"),  "G", 50, 10), new TierSeed("VIP", new BigDecimal("10000"), "V", 10, 5)), // Sports
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5)),   // Education
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5)),   // Entertainment
            List.of(new TierSeed("General", new BigDecimal("3000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("15000"), "V", 5, 5)),   // Health
            List.of(new TierSeed("General", new BigDecimal("10000"), "G", 10, 10), new TierSeed("VIP", new BigDecimal("50000"), "V", 4, 5)),   // Real Estate
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5))    // Fashion
    );

    // ─── event seed data (5 per user = 50 total) ─────────────────────────────────
    // free=true  → single "General" tier at ₦0 (500 seats)
    // free=false → theme-based General + VIP paid tiers

    private record EventSeed(
            String title,
            String description,
            int startDaysFromNow,
            int durationHours,
            EventStatus status,
            boolean free
    ) {}

    private static final List<List<EventSeed>> EVENTS_PER_USER = List.of(

            // Akin — Tech
            List.of(
                    new EventSeed("TechFest Lagos 2026",            "The biggest tech festival in West Africa.",              45,  8, EventStatus.PUBLISHED,       false),
                    new EventSeed("AI & Machine Learning Summit",    "Exploring cutting-edge AI applications.",                20,  6, EventStatus.PUBLISHED,       false),
                    new EventSeed("Blockchain Developers Bootcamp",  "Hands-on blockchain development training.",              60,  8, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Startup Pitch Night",             "Free event — early-stage founders pitch to investors.",  30,  4, EventStatus.DRAFT,            true),
                    new EventSeed("Digital Innovation Conference",   "Past conference on digital transformation.",            -20,  6, EventStatus.PUBLISHED,       false)
            ),

            // Chioma — Music & Arts
            List.of(
                    new EventSeed("Afrobeats Night Out",             "Live Afrobeats performances by top artists.",            15,  5, EventStatus.PUBLISHED,       false),
                    new EventSeed("Lagos Jazz Festival",             "Three days of world-class jazz.",                        90,  6, EventStatus.PUBLISHED,       false),
                    new EventSeed("Open Mic Wednesday",              "Free — discover emerging spoken word and music talent.", 7,   3, EventStatus.DRAFT,            true),
                    new EventSeed("Gospel Concert 2026",             "An uplifting evening of gospel music.",                  50,  4, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Highlife Revival Night",          "Celebrating the golden era of highlife music.",         -30,  5, EventStatus.PUBLISHED,       false)
            ),

            // Emeka — Business
            List.of(
                    new EventSeed("Entrepreneurs Summit 2026",       "Connecting founders, mentors and investors.",            35,  8, EventStatus.PUBLISHED,       false),
                    new EventSeed("SME Growth Conference",           "Practical strategies for small business growth.",        55,  6, EventStatus.PUBLISHED,       false),
                    new EventSeed("Investment & Finance Forum",      "Understanding the Nigerian capital markets.",            40,  5, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Business Networking Brunch",      "Free — curated networking for professionals.",           10,  3, EventStatus.DRAFT,            true),
                    new EventSeed("Export Trade Fair",               "Cancelled due to venue unavailability.",                 25,  8, EventStatus.CANCELLED,        false)
            ),

            // Funke — Food & Lifestyle
            List.of(
                    new EventSeed("Lagos Food & Wine Festival",      "Celebrating Nigeria's rich culinary culture.",           28,  8, EventStatus.PUBLISHED,       false),
                    new EventSeed("Healthy Living Expo",             "Wellness, nutrition and fitness under one roof.",        70,  6, EventStatus.PUBLISHED,       false),
                    new EventSeed("Street Food Carnival",            "Free — a tour of Lagos's best street food vendors.",     14,  5, EventStatus.DRAFT,            true),
                    new EventSeed("Culinary Arts Masterclass",       "Learn from award-winning Nigerian chefs.",               42,  4, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Farm-to-Table Dinner Experience", "An intimate dinner using locally sourced produce.",     -15,  4, EventStatus.PUBLISHED,       false)
            ),

            // Gbenga — Sports
            List.of(
                    new EventSeed("Lagos Marathon 2026",             "Annual marathon through the heart of Lagos.",           100,  8, EventStatus.PUBLISHED,       false),
                    new EventSeed("Inter-Company Football League",   "Corporate 5-a-side football tournament.",               22,  6, EventStatus.PUBLISHED,       false),
                    new EventSeed("Basketball Invitational",         "Top university basketball teams compete.",               33,  6, EventStatus.DRAFT,            false),
                    new EventSeed("Swimming Championship",           "National age-group swimming competition.",               48,  8, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Fitness & Wellness Weekend",      "Free — past weekend fitness retreat.",                  -10,  8, EventStatus.PUBLISHED,        true)
            ),

            // Halima — Education
            List.of(
                    new EventSeed("Women in STEM Conference",        "Inspiring the next generation of female engineers.",     38,  6, EventStatus.PUBLISHED,       false),
                    new EventSeed("Youth Leadership Summit",         "Empowering young leaders across Nigeria.",               65,  8, EventStatus.PUBLISHED,       false),
                    new EventSeed("Creative Writing Workshop",       "Free — develop your voice through guided writing.",      12,  4, EventStatus.DRAFT,            true),
                    new EventSeed("Science & Technology Fair",       "Showcasing student innovations and inventions.",         52,  8, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Graduate Career Expo",            "Free — cancelled, rescheduled to next quarter.",         18,  6, EventStatus.CANCELLED,        true)
            ),

            // Ifeanyi — Entertainment
            List.of(
                    new EventSeed("Comedy Night Live",               "Nigeria's funniest comedians on one stage.",              8,  3, EventStatus.PUBLISHED,       false),
                    new EventSeed("Nollywood Film Premiere",         "Exclusive premiere of an anticipated feature film.",     44,  3, EventStatus.PUBLISHED,       false),
                    new EventSeed("Stand-Up Showcase",               "Free — open showcase for emerging stand-up comedians.",  19,  3, EventStatus.DRAFT,            true),
                    new EventSeed("Fashion & Style Show",            "Runway show featuring Nigerian designers.",              36,  4, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Cultural Heritage Night",         "Free — celebrating Nigeria's diverse cultural traditions.", -25, 5, EventStatus.PUBLISHED,    true)
            ),

            // Jumoke — Health
            List.of(
                    new EventSeed("Mental Health Awareness Day",     "Free — breaking stigma and promoting mental wellness.",  26,  6, EventStatus.PUBLISHED,        true),
                    new EventSeed("Healthcare Innovation Summit",    "The future of healthcare delivery in Africa.",           80,  8, EventStatus.PUBLISHED,       false),
                    new EventSeed("Yoga & Mindfulness Retreat",      "Free — a full-day retreat focused on inner balance.",    16,  8, EventStatus.DRAFT,            true),
                    new EventSeed("Medical Research Symposium",      "Presenting breakthroughs in Nigerian healthcare.",       58,  6, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Community Health Fair",           "Free screenings and health education for all.",          -5,  6, EventStatus.PUBLISHED,        true)
            ),

            // Kola — Real Estate
            List.of(
                    new EventSeed("Lagos Property Expo",             "The premier real estate showcase in West Africa.",       32,  8, EventStatus.PUBLISHED,       false),
                    new EventSeed("Real Estate Investment Forum",    "Strategies for building a profitable property portfolio.",68, 6, EventStatus.PUBLISHED,       false),
                    new EventSeed("Architecture & Design Showcase",  "Free — featuring Nigeria's most innovative architects.", 23,  5, EventStatus.DRAFT,            true),
                    new EventSeed("Smart Cities Conference",         "Urban planning and technology for future cities.",       46,  6, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Housing Finance Summit",          "Cancelled — speaker scheduling conflict.",               29,  5, EventStatus.CANCELLED,        false)
            ),

            // Lara — Fashion
            List.of(
                    new EventSeed("Lagos Fashion Week",              "The continent's most prestigious fashion event.",        75,  8, EventStatus.PUBLISHED,       false),
                    new EventSeed("Designers Showcase 2026",         "Up-and-coming Nigerian designers take the spotlight.",   18,  5, EventStatus.PUBLISHED,       false),
                    new EventSeed("Fabric & Textile Market",         "Free — premium fabrics from across West Africa.",         9,  6, EventStatus.DRAFT,            true),
                    new EventSeed("Beauty & Cosmetics Expo",         "Celebrating African beauty brands and innovations.",     54,  6, EventStatus.PENDING_APPROVAL, false),
                    new EventSeed("Vintage Clothing Fair",           "A curated showcase of pre-loved fashion pieces.",       -40,  5, EventStatus.PUBLISHED,       false)
            )
    );

    // ─── runner ───────────────────────────────────────────────────────────────────

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(SEED_CHECK_EMAIL)) {
            log.info("Dev seed users present — checking for missing data");
            backfillMissingTiers();
            backfillMissingConfigs();
            backfillMissingDays();
            return;
        }

        List<User> users = seedUsers();
        seedEvents(users);

        log.info("Dev seed complete — {} users, 50 events", users.size());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private List<User> seedUsers() {
        List<User> saved = new ArrayList<>();
        for (String[] data : USERS) {
            User user = User.builder()
                    .firstName(data[0])
                    .lastName(data[1])
                    .email(data[2])
                    .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                    .role(Role.USER)
                    .enabled(true)
                    .build();
            saved.add(userRepository.save(user));
            log.debug("Seeded user: {} {}", data[0], data[1]);
        }
        return saved;
    }

    private void seedEvents(List<User> users) {
        int venueIndex = 0;
        int eventCount = 0;

        for (int i = 0; i < users.size(); i++) {
            User organizer = users.get(i);
            List<TierSeed> tierSeeds = TIERS_PER_THEME.get(i);

            for (EventSeed seed : EVENTS_PER_USER.get(i)) {
                LocalDateTime start = LocalDateTime.now()
                        .withHour(9).withMinute(0).withSecond(0).withNano(0)
                        .plusDays(seed.startDaysFromNow());
                LocalDateTime end = start.plusHours(seed.durationHours());
                LocalDateTime checkInStart = start.minusHours(2);

                Events event = Events.builder()
                        .title(seed.title())
                        .description(seed.description())
                        .venue(VENUES[venueIndex % VENUES.length])
                        .startTime(start)
                        .endTime(end)
                        .checkInStartTime(checkInStart)
                        .status(seed.status())
                        .createdBy(organizer)
                        .code(NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
                                NanoIdUtils.DEFAULT_ALPHABET, 8))
                        .build();

                Events saved = eventRepository.saveAndFlush(event);

                // EventConfig — defaults
                configRepository.save(EventConfig.builder()
                        .event(saved)
                        .ticketingEnabled(true)
                        .guestListEnabled(true)
                        .programmeEnabled(true)
                        .ratingsEnabled(false)
                        .build());

                // EventDay — single default day matching event window
                EventDay day = dayRepository.save(EventDay.builder()
                        .event(saved)
                        .dayNumber(1)
                        .startTime(start)
                        .endTime(end)
                        .checkInStartTime(checkInStart)
                        .venue(VENUES[venueIndex % VENUES.length])
                        .build());

                // Programme — add two sample items for published events with programme enabled
                if (seed.status() == EventStatus.PUBLISHED) {
                    programmeItemRepository.save(ProgrammeItem.builder()
                            .event(saved).eventDay(day)
                            .title("Opening Keynote")
                            .speakerName("Dr. Amara Osei")
                            .startTime(start).endTime(start.plusHours(1))
                            .displayOrder(0).build());
                    programmeItemRepository.save(ProgrammeItem.builder()
                            .event(saved).eventDay(day)
                            .title("Panel Discussion")
                            .speakerName("Various Speakers")
                            .startTime(start.plusHours(1)).endTime(start.plusHours(2))
                            .displayOrder(1).build());
                }

                List<TierSeed> tiersToSave = seed.free()
                        ? List.of(new TierSeed("General", BigDecimal.ZERO, "G", 50, 10))
                        : tierSeeds;

                tiersToSave.forEach(tier -> {
                    int capacity = tier.rowCount() * tier.seatsPerRow();
                    tierRepository.save(TicketTier.builder()
                            .event(saved)
                            .name(tier.name())
                            .price(tier.price())
                            .rowPrefix(tier.rowPrefix())
                            .rowCount(tier.rowCount())
                            .seatsPerRow(tier.seatsPerRow())
                            .totalCapacity(capacity)
                            .availableCapacity(capacity)
                            .build());
                });

                membershipRepository.save(EventMembership.builder()
                        .user(organizer)
                        .events(saved)
                        .role(EventRole.ORGANIZER)
                        .status(MembershipStatus.ACTIVE)
                        .build());

                venueIndex++;
                eventCount++;
                log.debug("Seeded event: [{}] {} ({}) ", seed.status(), seed.title(), seed.free() ? "FREE" : "PAID");
            }
        }

        log.info("Seeded {} events across {} organizers", eventCount, users.size());
    }

    private void backfillMissingConfigs() {
        int filled = 0;
        for (Events event : eventRepository.findAll()) {
            if (configRepository.findByEventId(event.getId()).isEmpty()) {
                configRepository.save(EventConfig.builder()
                        .event(event)
                        .ticketingEnabled(true)
                        .guestListEnabled(true)
                        .programmeEnabled(true)
                        .ratingsEnabled(false)
                        .build());
                filled++;
            }
        }
        if (filled > 0) log.info("Backfilled EventConfig for {} events", filled);
    }

    private void backfillMissingDays() {
        int filled = 0;
        for (Events event : eventRepository.findAll()) {
            if (dayRepository.countByEventId(event.getId()) == 0) {
                dayRepository.save(EventDay.builder()
                        .event(event)
                        .dayNumber(1)
                        .startTime(event.getStartTime())
                        .endTime(event.getEndTime())
                        .checkInStartTime(event.getCheckInStartTime())
                        .venue(event.getVenue())
                        .build());
                filled++;
            }
        }
        if (filled > 0) log.info("Backfilled EventDay for {} events", filled);
    }

    private void backfillMissingTiers() {
        int filled = 0;
        for (int i = 0; i < USERS.length; i++) {
            final int themeIndex = i;
            userRepository.findByEmail(USERS[i][2]).ifPresent(user -> {
                List<TierSeed> themeTiers = TIERS_PER_THEME.get(themeIndex);
                for (Events event : eventRepository.findAllByOrganizerId(user.getId())) {
                    if (!tierRepository.findAllByEventId(event.getId()).isEmpty()) continue;

                    boolean isFree = event.getDescription() != null
                            && event.getDescription().startsWith("Free");
                    List<TierSeed> tiersToAdd = isFree
                            ? List.of(new TierSeed("General", BigDecimal.ZERO, "G", 50, 10))
                            : themeTiers;

                    tiersToAdd.forEach(tier -> {
                        int capacity = tier.rowCount() * tier.seatsPerRow();
                        tierRepository.save(TicketTier.builder()
                                .event(event)
                                .name(tier.name())
                                .price(tier.price())
                                .rowPrefix(tier.rowPrefix())
                                .rowCount(tier.rowCount())
                                .seatsPerRow(tier.seatsPerRow())
                                .totalCapacity(capacity)
                                .availableCapacity(capacity)
                                .build());
                    });
                    log.debug("Backfilled tiers for: {} ({})", event.getTitle(), isFree ? "FREE" : "PAID");
                }
            });
            filled++;
        }
        log.info("Tier backfill complete for {} organizers", filled);
    }
}
