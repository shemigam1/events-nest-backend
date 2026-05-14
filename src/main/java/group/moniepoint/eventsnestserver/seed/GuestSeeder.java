package group.moniepoint.eventsnestserver.seed;

import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.guestlist.model.Guest;
import group.moniepoint.eventsnestserver.guestlist.model.RsvpStatus;
import group.moniepoint.eventsnestserver.guestlist.repository.GuestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class GuestSeeder {

    private final GuestRepository guestRepository;

    // ─── seed data ────────────────────────────────────────────────────────────

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

    // ─── seeding ─────────────────────────────────────────────────────────────

    void seed(List<List<Events>> eventsByUser) {
        int seededCount = 0;
        for (GuestSeed gs : GUEST_SEEDS) {
            Events event = eventsByUser.get(gs.organiserIndex()).get(gs.eventIndex());
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
        log.info("Seeded {} guests across private events", seededCount);
    }
}
