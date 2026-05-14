package group.moniepoint.eventsnestserver.seed;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.chat.model.Conversation;
import group.moniepoint.eventsnestserver.contracts.repository.VendorContractRepository;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.guestlist.repository.GuestRepository;
import group.moniepoint.eventsnestserver.programme.repository.ProgrammeItemRepository;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates dev data seeding on the "local" Spring profile.
 * Idempotent — skips the full seed if the sentinel user already exists;
 * still runs the tier backfill and a partial backfill for any data types
 * that were added after the initial seed (guests, programmes, contracts, tickets).
 *
 * Seeded data:
 *   - 32 users (5 verified vendors)
 *   - 160 events (5 per user: mix of statuses)
 *   - Ticket tiers (General + VIP) per paid event; free events get a ₦0 tier
 *   - Organiser memberships
 *   - Vendor applications (ACCEPTED / PENDING / REJECTED)
 *   - DM conversations + messages for every vendor application
 *   - Vendor contracts (SIGNED) with escrow accounts and 3-milestone payment plans
 *   - Programme items for all published events
 *   - Guest lists for private events
 *   - Confirmed bookings and valid tickets for published paid events
 */
@Slf4j
@Component
@Profile("local")
@Order(10)
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private static final String SEED_CHECK_EMAIL = "akin@devmail.com";

    private final UserRepository         userRepository;
    private final UserSeeder             userSeeder;
    private final EventSeeder            eventSeeder;
    private final VendorSeeder           vendorSeeder;
    private final ContractSeeder         contractSeeder;
    private final ProgrammeSeeder        programmeSeeder;
    private final GuestSeeder            guestSeeder;
    private final TicketSeeder           ticketSeeder;

    private final GuestRepository        guestRepository;
    private final ProgrammeItemRepository programmeItemRepository;
    private final VendorContractRepository vendorContractRepository;
    private final TicketRepository       ticketRepository;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(SEED_CHECK_EMAIL)) {
            log.info("Dev seed users present — checking for missing tiers and data");
            eventSeeder.backfillMissingTiers();
            backfillIfMissing();
            return;
        }

        List<User> users = userSeeder.seed();
        List<List<Events>> eventsByUser = eventSeeder.seed(users);

        List<VendorApplication> vendorApps = vendorSeeder.seedApplications(users, eventsByUser);
        Map<VendorApplication, Conversation> conversations =
                vendorSeeder.seedConversations(users, eventsByUser, vendorApps);
        vendorSeeder.seedMessages(conversations);

        contractSeeder.seed(vendorApps, conversations);
        programmeSeeder.seed(eventsByUser);
        guestSeeder.seed(eventsByUser);
        ticketSeeder.seed(users, eventsByUser);

        log.info("Dev seed complete — {} users, {} events, {} vendor apps, {} conversations",
                users.size(), users.size() * 5, vendorApps.size(), conversations.size());
    }

    private void backfillIfMissing() {
        boolean needsGuests    = guestRepository.count() == 0;
        boolean needsProgramme = programmeItemRepository.count() == 0;
        boolean needsContracts = vendorContractRepository.count() == 0;
        boolean needsTickets   = ticketRepository.count() == 0;

        if (!needsGuests && !needsProgramme && !needsContracts && !needsTickets) {
            log.info("All seed data present — nothing to backfill");
            return;
        }

        log.info("Backfilling missing seed data: guests={}, programme={}, contracts={}, tickets={}",
                needsGuests, needsProgramme, needsContracts, needsTickets);

        List<User> users = userSeeder.reloadOrdered();
        List<List<Events>> eventsByUser = eventSeeder.reloadGroupedByUser(users);

        if (needsGuests) {
            guestSeeder.seed(eventsByUser);
        }
        if (needsProgramme) {
            programmeSeeder.seed(eventsByUser);
        }
        if (needsContracts) {
            List<VendorApplication> vendorApps = vendorSeeder.reloadApps();
            Map<VendorApplication, Conversation> conversations = vendorSeeder.reloadConversations(vendorApps);
            contractSeeder.seed(vendorApps, conversations);
        }
        if (needsTickets) {
            ticketSeeder.seed(users, eventsByUser);
        }

        log.info("Backfill complete");
    }
}
