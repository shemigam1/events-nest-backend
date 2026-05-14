package group.moniepoint.eventsnestserver.seed;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.events.models.*;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class EventSeeder {

    private final EventRespository          eventRepository;
    private final EventConfigRepository     eventConfigRepository;
    private final EventMembershipRepository membershipRepository;
    private final TicketTierRepository      tierRepository;
    private final UserRepository            userRepository;

    // ─── venues ──────────────────────────────────────────────────────────────

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

    // ─── tier definitions per organiser theme ─────────────────────────────────

    record TierSeed(String name, BigDecimal price, String rowPrefix, int rowCount, int seatsPerRow) {}

    static final List<List<TierSeed>> TIERS_PER_THEME = List.of(
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("25000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("8000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("30000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("10000"), "G", 15, 10), new TierSeed("VIP", new BigDecimal("50000"), "V", 4, 5)),
            List.of(new TierSeed("General", new BigDecimal("3000"),  "G", 30, 10), new TierSeed("VIP", new BigDecimal("15000"), "V", 6, 5)),
            List.of(new TierSeed("General", new BigDecimal("2000"),  "G", 50, 10), new TierSeed("VIP", new BigDecimal("10000"), "V", 10, 5)),
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("3000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("15000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("10000"), "G", 10, 10), new TierSeed("VIP", new BigDecimal("50000"), "V", 4, 5)),
            List.of(new TierSeed("General", new BigDecimal("5000"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("4000"),  "G", 25, 10), new TierSeed("VIP", new BigDecimal("18000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("6000"),  "G", 18, 10), new TierSeed("VIP", new BigDecimal("28000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("3500"),  "G", 30, 10), new TierSeed("VIP", new BigDecimal("16000"), "V", 6, 5)),
            List.of(new TierSeed("General", new BigDecimal("5500"),  "G", 22, 10), new TierSeed("VIP", new BigDecimal("22000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("7000"),  "G", 16, 10), new TierSeed("VIP", new BigDecimal("35000"), "V", 4, 5)),
            List.of(new TierSeed("General", new BigDecimal("4500"),  "G", 24, 10), new TierSeed("VIP", new BigDecimal("20000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("6500"),  "G", 19, 10), new TierSeed("VIP", new BigDecimal("32000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("3200"),  "G", 35, 10), new TierSeed("VIP", new BigDecimal("14000"), "V", 7, 5)),
            List.of(new TierSeed("General", new BigDecimal("5200"),  "G", 21, 10), new TierSeed("VIP", new BigDecimal("19000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("8500"),  "G", 17, 10), new TierSeed("VIP", new BigDecimal("40000"), "V", 4, 5)),
            List.of(new TierSeed("General", new BigDecimal("4700"),  "G", 23, 10), new TierSeed("VIP", new BigDecimal("21000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("5800"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("26000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("6200"),  "G", 18, 10), new TierSeed("VIP", new BigDecimal("29000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("3800"),  "G", 32, 10), new TierSeed("VIP", new BigDecimal("17000"), "V", 6, 5)),
            List.of(new TierSeed("General", new BigDecimal("7200"),  "G", 15, 10), new TierSeed("VIP", new BigDecimal("38000"), "V", 4, 5)),
            List.of(new TierSeed("General", new BigDecimal("4300"),  "G", 26, 10), new TierSeed("VIP", new BigDecimal("19500"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("6800"),  "G", 19, 10), new TierSeed("VIP", new BigDecimal("31000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("5400"),  "G", 20, 10), new TierSeed("VIP", new BigDecimal("24000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("7500"),  "G", 14, 10), new TierSeed("VIP", new BigDecimal("42000"), "V", 4, 5)),
            List.of(new TierSeed("General", new BigDecimal("4100"),  "G", 28, 10), new TierSeed("VIP", new BigDecimal("18500"), "V", 6, 5)),
            List.of(new TierSeed("General", new BigDecimal("6900"),  "G", 17, 10), new TierSeed("VIP", new BigDecimal("33000"), "V", 5, 5)),
            List.of(new TierSeed("General", new BigDecimal("5600"),  "G", 21, 10), new TierSeed("VIP", new BigDecimal("25500"), "V", 5, 5))
    );

    // ─── events (5 per user) ─────────────────────────────────────────────────

    record EventSeed(
            String title, String description,
            int startDaysFromNow, int durationHours,
            EventStatus status, boolean free,
            EventVisibility visibility, boolean guestListEnabled) {}

    private static EventSeed pub(String title, String desc, int days, int hrs, EventStatus status, boolean free) {
        return new EventSeed(title, desc, days, hrs, status, free, EventVisibility.PUBLIC, false);
    }

    private static EventSeed priv(String title, String desc, int days, int hrs, EventStatus status, boolean free) {
        return new EventSeed(title, desc, days, hrs, status, free, EventVisibility.PRIVATE, true);
    }

    static final List<List<EventSeed>> EVENTS_PER_USER = List.of(

            // Akin — Tech (index 0)
            List.of(
                    pub( "TechFest Lagos 2026",            "The biggest tech festival in West Africa.",                45,  8, EventStatus.PUBLISHED,        false),
                    pub( "AI & Machine Learning Summit",   "Exploring cutting-edge AI applications.",                  20,  6, EventStatus.PUBLISHED,        false),
                    priv("Blockchain Developers Bootcamp", "Hands-on blockchain development training — invite-only.",  60,  8, EventStatus.PUBLISHED,        false),
                    pub( "Startup Pitch Night",            "Free — early-stage founders pitch to investors.",          30,  4, EventStatus.DRAFT,            true),
                    pub( "Digital Innovation Conference",  "Past conference on digital transformation.",              -20,  6, EventStatus.PUBLISHED,        false)
            ),

            // Chioma — Music & Arts (index 1)
            List.of(
                    pub( "Afrobeats Night Out",            "Live Afrobeats performances by top artists.",              15,  5, EventStatus.PUBLISHED,        false),
                    pub( "Lagos Jazz Festival",            "Three days of world-class jazz.",                          90,  6, EventStatus.PUBLISHED,        false),
                    pub( "Open Mic Wednesday",             "Free — discover emerging spoken word and music talent.",    7,  3, EventStatus.DRAFT,            true),
                    priv("Gospel Concert 2026",            "An uplifting evening of gospel music — members only.",     50,  4, EventStatus.PUBLISHED,        false),
                    pub( "Highlife Revival Night",         "Celebrating the golden era of highlife music.",           -30,  5, EventStatus.PUBLISHED,        false)
            ),

            // Emeka — Business (index 2)
            List.of(
                    pub( "Entrepreneurs Summit 2026",      "Connecting founders, mentors and investors.",              35,  8, EventStatus.PUBLISHED,        false),
                    priv("SME Growth Conference",          "Practical strategies for small business growth — by invitation.", 55, 6, EventStatus.PUBLISHED, false),
                    pub( "Investment & Finance Forum",     "Understanding the Nigerian capital markets.",               40,  5, EventStatus.PENDING_APPROVAL, false),
                    pub( "Business Networking Brunch",     "Free — curated networking for professionals.",             10,  3, EventStatus.DRAFT,            true),
                    pub( "Export Trade Fair",              "Cancelled due to venue unavailability.",                   25,  8, EventStatus.CANCELLED,        false)
            ),

            // Funke — Food & Lifestyle (index 3)
            List.of(
                    pub( "Lagos Food & Wine Festival",     "Celebrating Nigeria's rich culinary culture.",             28,  8, EventStatus.PUBLISHED,        false),
                    pub( "Healthy Living Expo",            "Wellness, nutrition and fitness under one roof.",          70,  6, EventStatus.PUBLISHED,        false),
                    pub( "Street Food Carnival",           "Free — a tour of Lagos's best street food vendors.",       14,  5, EventStatus.DRAFT,            true),
                    priv("Culinary Arts Masterclass",      "Learn from award-winning chefs — intimate class, limited seats.", 42, 4, EventStatus.PUBLISHED,   false),
                    pub( "Farm-to-Table Dinner Experience","An intimate dinner using locally sourced produce.",        -15,  4, EventStatus.PUBLISHED,        false)
            ),

            // Gbenga — Sports (index 4)
            List.of(
                    pub( "Lagos Marathon 2026",            "Annual marathon through the heart of Lagos.",             100,  8, EventStatus.PUBLISHED,        false),
                    pub( "Inter-Company Football League",  "Corporate 5-a-side football tournament.",                  22,  6, EventStatus.PUBLISHED,        false),
                    pub( "Basketball Invitational",        "Top university basketball teams compete.",                  33,  6, EventStatus.DRAFT,            false),
                    priv("Swimming Championship",          "National age-group swimming competition — participants and families only.", 48, 8, EventStatus.PUBLISHED, false),
                    pub( "Fitness & Wellness Weekend",     "Free — past weekend fitness retreat.",                    -10,  8, EventStatus.PUBLISHED,        true)
            ),

            // Halima — Education (index 5)
            List.of(
                    pub( "Women in STEM Conference",       "Inspiring the next generation of female engineers.",       38,  6, EventStatus.PUBLISHED,        false),
                    pub( "Youth Leadership Summit",        "Empowering young leaders across Nigeria.",                 65,  8, EventStatus.PUBLISHED,        false),
                    pub( "Creative Writing Workshop",      "Free — develop your voice through guided writing.",        12,  4, EventStatus.DRAFT,            true),
                    pub( "Science & Technology Fair",      "Showcasing student innovations and inventions.",           52,  8, EventStatus.PENDING_APPROVAL, false),
                    pub( "Graduate Career Expo",           "Free — cancelled, rescheduled to next quarter.",           18,  6, EventStatus.CANCELLED,        true)
            ),

            // Ifeanyi — Entertainment (index 6)
            List.of(
                    pub( "Comedy Night Live",              "Nigeria's funniest comedians on one stage.",                8,  3, EventStatus.PUBLISHED,        false),
                    priv("Nollywood Film Premiere",        "Exclusive premiere of an anticipated feature film — press and VIP guests only.", 44, 3, EventStatus.PUBLISHED, false),
                    pub( "Stand-Up Showcase",              "Free — open showcase for emerging stand-up comedians.",    19,  3, EventStatus.DRAFT,            true),
                    pub( "Fashion & Style Show",           "Runway show featuring Nigerian designers.",                 36,  4, EventStatus.PENDING_APPROVAL, false),
                    pub( "Cultural Heritage Night",        "Free — celebrating Nigeria's diverse cultural traditions.",-25,  5, EventStatus.PUBLISHED,       true)
            ),

            // Jumoke — Health (index 7)
            List.of(
                    pub( "Mental Health Awareness Day",    "Free — breaking stigma and promoting mental wellness.",    26,  6, EventStatus.PUBLISHED,        true),
                    pub( "Healthcare Innovation Summit",   "The future of healthcare delivery in Africa.",             80,  8, EventStatus.PUBLISHED,        false),
                    priv("Yoga & Mindfulness Retreat",     "Free — a full-day retreat for registered participants only.", 16, 8, EventStatus.PUBLISHED,      true),
                    pub( "Medical Research Symposium",     "Presenting breakthroughs in Nigerian healthcare.",          58,  6, EventStatus.PENDING_APPROVAL, false),
                    pub( "Community Health Fair",          "Free — screenings and health education for all.",           -5,  6, EventStatus.PUBLISHED,       true)
            ),

            // Kola — Real Estate (index 8)
            List.of(
                    pub( "Lagos Property Expo",            "The premier real estate showcase in West Africa.",          32,  8, EventStatus.PUBLISHED,        false),
                    priv("Real Estate Investment Forum",   "Strategies for building a profitable property portfolio — accredited investors only.", 68, 6, EventStatus.PUBLISHED, false),
                    pub( "Architecture & Design Showcase", "Free — featuring Nigeria's most innovative architects.",    23,  5, EventStatus.DRAFT,            true),
                    pub( "Smart Cities Conference",        "Urban planning and technology for future cities.",          46,  6, EventStatus.PENDING_APPROVAL, false),
                    pub( "Housing Finance Summit",         "Cancelled — speaker scheduling conflict.",                  29,  5, EventStatus.CANCELLED,        false)
            ),

            // Lara — Fashion (index 9)
            List.of(
                    pub( "Lagos Fashion Week",             "The continent's most prestigious fashion event.",           75,  8, EventStatus.PUBLISHED,        false),
                    pub( "Designers Showcase 2026",        "Up-and-coming Nigerian designers take the spotlight.",      18,  5, EventStatus.PUBLISHED,        false),
                    priv("Fabric & Textile Market",        "Free — premium fabrics, trade buyers and designers only.",   9,  6, EventStatus.PUBLISHED,        true),
                    pub( "Beauty & Cosmetics Expo",        "Celebrating African beauty brands and innovations.",         54,  6, EventStatus.PENDING_APPROVAL, false),
                    pub( "Vintage Clothing Fair",          "A curated showcase of pre-loved fashion pieces.",           -40,  5, EventStatus.PUBLISHED,       false)
            ),

            // Musa — Marketing & Growth (index 10)
            List.of(
                    pub( "Digital Marketing Summit 2026",  "Mastering modern digital strategies and SEO.",              42,  7, EventStatus.PUBLISHED,        false),
                    pub( "Growth Hacking Workshop",        "Learn proven tactics to scale your startup.",               16,  5, EventStatus.PUBLISHED,        false),
                    priv("Brand Strategy Intensive",       "Hands-on workshop for brand positioning — limited seats.",   28,  8, EventStatus.PUBLISHED,        false),
                    pub( "Social Media Masterclass",       "Free — maximize your social media presence.",               11,  4, EventStatus.DRAFT,            true),
                    pub( "Marketing Analytics Forum",      "Data-driven decisions in modern marketing.",               -12,  5, EventStatus.PUBLISHED,        false)
            ),

            // Ngozi — Media & Broadcasting (index 11)
            List.of(
                    pub( "Media Production Conference",    "Latest trends in broadcast and digital media.",             37,  8, EventStatus.PUBLISHED,        false),
                    pub( "Podcast & Audio Summit",         "The rise of audio content in Nigeria.",                     22,  6, EventStatus.PUBLISHED,        false),
                    priv("Content Creator Roundtable",     "Free — exclusive discussion with top creators.",            13,  4, EventStatus.PUBLISHED,        true),
                    pub( "Television & Film Festival",     "Celebrating Nigerian storytelling excellence.",             67,  8, EventStatus.PENDING_APPROVAL, false),
                    pub( "Journalism Ethics Forum",        "Ethical reporting in the digital age.",                    -18,  5, EventStatus.PUBLISHED,        false)
            ),

            // Oluwatoyin — Wellness & Fitness (index 12)
            List.of(
                    pub( "Holistic Health Expo 2026",      "Mind, body, and spirit wellness integration.",              32,  7, EventStatus.PUBLISHED,        false),
                    pub( "Fitness Challenge Series",       "12-week community fitness transformation.",                25,  6, EventStatus.PUBLISHED,        false),
                    priv("Personal Training Certification","Become a certified fitness trainer — professional track.",    41,  8, EventStatus.PUBLISHED,        false),
                    pub( "Nutrition & Diet Workshop",      "Free — science-backed nutrition for optimal health.",        8,  4, EventStatus.DRAFT,            true),
                    pub( "Wellness Retreat",               "A weekend of rejuvenation and self-care.",                  -22,  8, EventStatus.PUBLISHED,        false)
            ),

            // Precious — Arts & Culture (index 13)
            List.of(
                    pub( "Contemporary Art Exhibition",    "Showcasing emerging Nigerian visual artists.",              31,  7, EventStatus.PUBLISHED,        false),
                    pub( "Sculpture & Installation Art",   "A deep dive into 3D artistic expression.",                  17,  5, EventStatus.PUBLISHED,        false),
                    priv("Art Collectors' Dinner",         "Free — curated evening for art enthusiasts.",               19,  5, EventStatus.PUBLISHED,        true),
                    pub( "Performing Arts Showcase",       "Dance, theatre, and live performance festival.",            49,  8, EventStatus.PENDING_APPROVAL, false),
                    pub( "Traditional Crafts Market",      "Free — celebrating Nigerian artisanal traditions.",        -35,  6, EventStatus.PUBLISHED,       true)
            ),

            // Rasheed — Logistics & Supply Chain (index 14)
            List.of(
                    pub( "Supply Chain Optimization Forum","Best practices in logistics and procurement.",              39,  7, EventStatus.PUBLISHED,        false),
                    pub( "Warehouse Management Summit",    "Modern warehousing and inventory systems.",                 21,  6, EventStatus.PUBLISHED,        false),
                    priv("Logistics Technology Workshop",  "AI and automation in supply chains — professionals only.",  33,  8, EventStatus.PUBLISHED,        false),
                    pub( "Last-Mile Delivery Conference",  "Free — the future of delivery networks.",                   14,  4, EventStatus.DRAFT,            true),
                    pub( "Trade & Customs Forum",          "International shipping and regulatory compliance.",         -28,  5, EventStatus.PUBLISHED,        false)
            ),

            // Seun — Tech Summit (index 15)
            List.of(
                    pub( "Web3 & Blockchain Summit",       "The future of decentralized applications.",                 44,  8, EventStatus.PUBLISHED,        false),
                    pub( "Cloud Architecture Masterclass", "Building scalable cloud solutions.",                        20,  6, EventStatus.PUBLISHED,        false),
                    priv("CTO Roundtable & Networking",    "Exclusive for tech leaders and decision makers.",           56,  4, EventStatus.PUBLISHED,        false),
                    pub( "Cybersecurity Workshop",         "Free — protect your digital assets.",                       12,  5, EventStatus.DRAFT,            true),
                    pub( "Startup Tech Stack Seminar",     "Choosing the right tools for your startup.",               -15,  5, EventStatus.PUBLISHED,        false)
            ),

            // Tunde — Finance & Investment (index 16)
            List.of(
                    pub( "Investment Summit 2026",         "Wealth building and portfolio management.",                 38,  8, EventStatus.PUBLISHED,        false),
                    pub( "Cryptocurrency & Digital Assets","Understanding crypto investments.",                         23,  6, EventStatus.PUBLISHED,        false),
                    priv("Private Banking Forum",          "Wealth management for high-net-worth individuals.",         51,  6, EventStatus.PUBLISHED,        false),
                    pub( "Financial Literacy Workshop",    "Free — foundations of personal finance.",                    9,  4, EventStatus.DRAFT,            true),
                    pub( "Stock Market Investing Guide",   "Beginner's guide to the Nigerian stock exchange.",         -24,  5, EventStatus.PUBLISHED,        false)
            ),

            // Uche — Community Development (index 17)
            List.of(
                    pub( "Community Leadership Forum",     "Building stronger, more connected communities.",            36,  7, EventStatus.PUBLISHED,        false),
                    pub( "Social Impact Workshop",         "Creating positive change in your community.",               19,  5, EventStatus.PUBLISHED,        false),
                    priv("NGO Strategic Planning",         "Effective non-profit management and governance.",           27,  8, EventStatus.PUBLISHED,        false),
                    pub( "Volunteerism & Giving Expo",     "Free — ways to make a difference.",                         10,  5, EventStatus.DRAFT,            true),
                    pub( "Community Health Initiative",    "Public health and wellness in local areas.",               -20,  6, EventStatus.PUBLISHED,        false)
            ),

            // Vicky — Cultural Exchange (index 18)
            List.of(
                    pub( "African Culture Festival",       "Celebrating the diversity of African heritage.",            40,  8, EventStatus.PUBLISHED,        false),
                    pub( "Traditional Music & Dance",      "Exploring African rhythms and movement.",                   24,  6, EventStatus.PUBLISHED,        false),
                    priv("Cultural Ambassadors Gala",      "Free — celebrating cultural excellence.",                   29,  5, EventStatus.PUBLISHED,        true),
                    pub( "Language & Heritage Symposium",  "Preserving African languages and traditions.",              47,  7, EventStatus.PENDING_APPROVAL, false),
                    pub( "Diaspora Dialogue",              "Connecting Africans at home and abroad.",                  -30,  5, EventStatus.PUBLISHED,        false)
            ),

            // Wuraola — Innovation & Entrepreneurship (index 19)
            List.of(
                    pub( "Innovation Expo 2026",           "Showcasing game-changing technologies and ideas.",          48,  8, EventStatus.PUBLISHED,        false),
                    pub( "Deep Tech Bootcamp",             "Advanced technology for solving real problems.",             26,  7, EventStatus.PUBLISHED,        false),
                    priv("Innovation Board Meeting",       "Strategic discussions on future technologies.",             53,  4, EventStatus.PUBLISHED,        false),
                    pub( "Prototyping Workshop",           "Free — from idea to prototype in one day.",                 15,  5, EventStatus.DRAFT,            true),
                    pub( "Tech for Good Summit",           "Innovation solving social problems.",                      -25,  6, EventStatus.PUBLISHED,        false)
            ),

            // Xolani — Entrepreneurship Summit (index 20)
            List.of(
                    pub( "Annual Entrepreneurs Summit",    "The largest gathering of business builders.",               45,  8, EventStatus.PUBLISHED,        false),
                    pub( "Pre-Launch Bootcamp",            "Getting your startup ready for market.",                    19,  6, EventStatus.PUBLISHED,        false),
                    priv("Founders' Mastermind Group",     "Exclusive peer learning for experienced founders.",         34,  8, EventStatus.PUBLISHED,        false),
                    pub( "Lean Startup Workshop",          "Free — building lean, customer-centric businesses.",        11,  4, EventStatus.DRAFT,            true),
                    pub( "Exit Strategy Forum",            "Planning and executing successful business exits.",        -32,  5, EventStatus.PUBLISHED,        false)
            ),

            // Yetunde — Expo & Events (index 21)
            List.of(
                    pub( "Major Trade Expo 2026",          "The largest gathering of vendors and buyers.",              46,  8, EventStatus.PUBLISHED,        false),
                    pub( "Consumer Products Showcase",     "Latest innovations in consumer goods.",                     21,  6, EventStatus.PUBLISHED,        false),
                    priv("VIP Networking Reception",       "Free — exclusive for exhibitors and partners.",             25,  3, EventStatus.PUBLISHED,        true),
                    pub( "E-commerce Marketplace Forum",   "Selling online and growing your business.",                 50,  7, EventStatus.PENDING_APPROVAL, false),
                    pub( "Vendor Training Summit",         "Skills for succeeding as a vendor or merchant.",           -28,  5, EventStatus.PUBLISHED,        false)
            ),

            // Zainab — Conference Series (index 22)
            List.of(
                    pub( "Annual Business Conference",     "Insights from industry leaders and innovators.",            41,  8, EventStatus.PUBLISHED,        false),
                    pub( "Leadership Development Program", "Strategies for leading in uncertain times.",                18,  6, EventStatus.PUBLISHED,        false),
                    priv("Executive Breakfast Series",     "Free — intimate sessions with C-suite leaders.",           30,  2, EventStatus.PUBLISHED,        true),
                    pub( "Change Management Workshop",     "Leading transformation in your organization.",              44,  7, EventStatus.PENDING_APPROVAL, false),
                    pub( "Strategic Planning Retreat",     "Annual strategy and planning session.",                    -26,  8, EventStatus.PUBLISHED,        false)
            ),

            // Amara — Professional Development Workshop (index 23)
            List.of(
                    pub( "Skills Development Bootcamp",    "Upskilling for the modern workplace.",                      35,  7, EventStatus.PUBLISHED,        false),
                    pub( "Leadership Coaching Program",    "Personal coaching for emerging leaders.",                   17,  6, EventStatus.PUBLISHED,        false),
                    priv("Professional Certification Course","Earn recognized credentials in your field.",              43,  8, EventStatus.PUBLISHED,        false),
                    pub( "Career Transition Workshop",     "Free — navigating career changes.",                          7,  4, EventStatus.DRAFT,            true),
                    pub( "Workplace Communication Summit", "Effective communication at all levels.",                   -19,  5, EventStatus.PUBLISHED,        false)
            ),

            // Bolaji — Business Forum (index 24)
            List.of(
                    pub( "Annual Business Leaders Forum",  "Networking and insights for business owners.",              43,  8, EventStatus.PUBLISHED,        false),
                    pub( "Corporate Governance Summit",    "Best practices in business governance.",                    20,  6, EventStatus.PUBLISHED,        false),
                    priv("Board Members' Roundtable",      "Strategic discussions for board leadership.",               52,  4, EventStatus.PUBLISHED,        false),
                    pub( "Business Ethics & Compliance",   "Free — navigating regulatory requirements.",               13,  5, EventStatus.DRAFT,            true),
                    pub( "Franchise Opportunity Expo",     "Exploring franchise business models.",                    -29,  6, EventStatus.PUBLISHED,        false)
            ),

            // Chidinma — Festival Celebration (index 25)
            List.of(
                    pub( "Annual Celebration Festival",    "A festival of food, music, and community.",                 50,  8, EventStatus.PUBLISHED,        false),
                    pub( "Cultural Food Festival",         "Traditional cuisines from across the continent.",           27,  6, EventStatus.PUBLISHED,        false),
                    priv("Festival VIP Lounge",            "Free — exclusive festival experience.",                     26,  4, EventStatus.PUBLISHED,        true),
                    pub( "Music & Entertainment Gala",     "Live performances and entertainment.",                      55,  7, EventStatus.PENDING_APPROVAL, false),
                    pub( "Community Celebration Carnival", "Free — a day of fun for the whole family.",               -33,  8, EventStatus.PUBLISHED,       true)
            ),

            // Daniel — Retreat & Renewal (index 26)
            List.of(
                    pub( "Annual Wellness Retreat",        "Renewal and restoration in a peaceful setting.",            60,  3, EventStatus.PUBLISHED,        false),
                    pub( "Mindfulness & Meditation Workshop","Transform your inner peace and clarity.",                28,  7, EventStatus.PUBLISHED,        false),
                    priv("Executive Retreat Getaway",      "Strategic planning in a serene setting.",                   38,  3, EventStatus.PUBLISHED,        false),
                    pub( "Nature & Wellness Escape",       "Free — connect with nature and self.",                      14,  3, EventStatus.DRAFT,            true),
                    pub( "Team Building Retreat",          "Strengthen bonds and align team goals.",                   -21,  3, EventStatus.PUBLISHED,        false)
            ),

            // Ebube — Gala Dinner Event (index 27)
            List.of(
                    pub( "Annual Charity Gala Dinner",     "Fine dining benefiting local charities.",                   52,  4, EventStatus.PUBLISHED,        false),
                    pub( "Awards & Recognition Gala",      "Celebrating excellence and achievements.",                  30,  4, EventStatus.PUBLISHED,        false),
                    priv("VIP Gala Reception",             "Exclusive evening for VIP patrons.",                        36,  4, EventStatus.PUBLISHED,        false),
                    pub( "Black Tie Dinner Benefit",       "Elegant dinner supporting social causes.",                 48,  4, EventStatus.PENDING_APPROVAL, false),
                    pub( "Anniversary Celebration Gala",   "Commemorating milestones and memories.",                  -31,  4, EventStatus.PUBLISHED,        false)
            ),

            // Folake — Symposium & Research (index 28)
            List.of(
                    pub( "Annual Research Symposium",      "Sharing latest findings and innovations.",                 40,  8, EventStatus.PUBLISHED,        false),
                    pub( "Academic Excellence Forum",      "Advancing knowledge and critical thinking.",                22,  6, EventStatus.PUBLISHED,        false),
                    priv("Research Collaboration Summit",  "Networking for academic and research professionals.",       54,  8, EventStatus.PUBLISHED,        false),
                    pub( "Student Research Showcase",      "Free — highlighting student research projects.",            6,  5, EventStatus.DRAFT,            true),
                    pub( "Think Tank Discussion Series",   "Deep dives into critical societal issues.",               -23,  6, EventStatus.PUBLISHED,        false)
            ),

            // Gbemi — Carnival & Entertainment (index 29)
            List.of(
                    pub( "Annual Street Carnival",         "Music, dance, food, and celebration.",                     51,  8, EventStatus.PUBLISHED,        false),
                    pub( "Entertainment & Comedy Fest",    "Laughter and entertainment all day long.",                  28,  6, EventStatus.PUBLISHED,        false),
                    priv("Behind-the-Scenes Carnival Tour","Free — exclusive carnival access and tours.",               22,  4, EventStatus.PUBLISHED,        true),
                    pub( "Carnival Parade & Showcase",     "Grand parade with floats and performers.",                 57,  6, EventStatus.PENDING_APPROVAL, false),
                    pub( "Family Fun Day Carnival",        "Free — entertainment for all ages.",                       -34,  8, EventStatus.PUBLISHED,       true)
            ),

            // Habiba — Awards Ceremony (index 30)
            List.of(
                    pub( "Annual Excellence Awards",       "Honoring achievers and leaders.",                           47,  4, EventStatus.PUBLISHED,        false),
                    pub( "Industry Innovation Awards",     "Recognizing the best innovations.",                         25,  4, EventStatus.PUBLISHED,        false),
                    priv("Awards Gala Dinner",             "Exclusive celebration for award recipients.",              37,  4, EventStatus.PUBLISHED,        false),
                    pub( "Community Heroes Recognition",   "Free — celebrating unsung heroes.",                         16,  3, EventStatus.DRAFT,            true),
                    pub( "Lifetime Achievement Awards",    "Celebrating legendary contributions.",                    -27,  4, EventStatus.PUBLISHED,        false)
            ),

            // Ibrahim — Showcase & Demonstration (index 31)
            List.of(
                    pub( "Product Launch Showcase",        "Introducing latest innovations.",                          42,  6, EventStatus.PUBLISHED,        false),
                    pub( "Live Demo & Presentation Event", "Interactive product demonstrations.",                      24,  5, EventStatus.PUBLISHED,        false),
                    priv("Early Access Preview",           "Free — exclusive first look at new products.",             32,  3, EventStatus.PUBLISHED,        true),
                    pub( "Technology Showcase Fair",       "See the future of technology today.",                      58,  8, EventStatus.PENDING_APPROVAL, false),
                    pub( "Innovation Marketplace",         "Explore groundbreaking solutions.",                        -29,  6, EventStatus.PUBLISHED,        false)
            )
    );

    // ─── seeding ─────────────────────────────────────────────────────────────

    List<List<Events>> seed(List<User> users) {
        List<List<Events>> result = new ArrayList<>();
        int venueIndex = 0;

        for (int i = 0; i < users.size(); i++) {
            User organizer = users.get(i);
            List<TierSeed> tierSeeds = TIERS_PER_THEME.get(i);
            List<Events> userEvents = new ArrayList<>();

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

        log.info("Seeded {} events across {} organizers", users.size() * 5, users.size());
        return result;
    }

    void backfillMissingTiers() {
        int filled = 0;
        for (int i = 0; i < UserSeeder.USERS.length; i++) {
            final int idx = i;
            userRepository.findByEmail(UserSeeder.USERS[i][2]).ifPresent(user -> {
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

    List<List<Events>> reloadGroupedByUser(List<group.moniepoint.eventsnestserver.auth.model.User> users) {
        List<List<Events>> result = new ArrayList<>();
        for (group.moniepoint.eventsnestserver.auth.model.User u : users) {
            result.add(eventRepository.findAllByOrganizerIdAsc(u.getId()));
        }
        return result;
    }
}
