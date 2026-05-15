package group.moniepoint.eventsnestserver.seed;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.budget.repository.EventBudgetRepository;
import group.moniepoint.eventsnestserver.chat.model.Conversation;
import group.moniepoint.eventsnestserver.contracts.repository.VendorContractRepository;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.guestlist.repository.GuestRepository;
import group.moniepoint.eventsnestserver.programme.repository.ProgrammeItemRepository;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import group.moniepoint.eventsnestserver.vendor.repository.VendorRatingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates dev/demo data seeding.
 * Idempotent — skips the full seed if the sentinel user already exists;
 * still runs the tier backfill and a partial backfill for any data types
 * that were added after the initial seed (guests, programmes, contracts,
 * tickets, budgets, reviews, Semicolon/DreamDev).
 *
 * Enabled when {@code app.seeder.enabled=true} (default true).
 * Set {@code APP_SEEDER_ENABLED=false} in the environment to disable.
 *
 * Seeded data:
 *   - 33 users (5 verified vendors + Semicolon organiser)
 *   - 161 events (5 per user + DreamDev Test Demo)
 *   - Ticket tiers (General + VIP) per paid event; free events get a ₦0 tier
 *   - Organiser memberships
 *   - Vendor applications (ACCEPTED / PENDING / REJECTED) — Akin has contracts
 *     on all 3 of his remaining published events
 *   - DM conversations + messages for every vendor application
 *   - Vendor contracts (SIGNED) with escrow accounts and 3-milestone payment plans
 *   - Programme items for all published events (incl. Group 1–9 for DreamDev)
 *   - Guest lists for private events
 *   - Confirmed bookings and valid tickets for published paid events
 *   - Event budgets (Akin's 4 published events + DreamDev)
 *   - Vendor ratings (reviews) for all ACCEPTED vendor applications
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true")
@Order(10)
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private static final String SEED_CHECK_EMAIL     = "akin@devmail.com";
    private static final String SEMICOLON_EMAIL       = "semicolon@devmail.com";

    private final UserRepository            userRepository;
    private final UserSeeder                userSeeder;
    private final EventSeeder               eventSeeder;
    private final VendorSeeder              vendorSeeder;
    private final ContractSeeder            contractSeeder;
    private final ProgrammeSeeder           programmeSeeder;
    private final GuestSeeder               guestSeeder;
    private final TicketSeeder              ticketSeeder;
    private final BudgetSeeder              budgetSeeder;
    private final ReviewSeeder              reviewSeeder;

    private final GuestRepository           guestRepository;
    private final ProgrammeItemRepository   programmeItemRepository;
    private final VendorContractRepository  vendorContractRepository;
    private final TicketRepository          ticketRepository;
    private final EventBudgetRepository     eventBudgetRepository;
    private final VendorRatingRepository    vendorRatingRepository;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(SEED_CHECK_EMAIL)) {
            log.info("Dev seed users present — checking for missing data");
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
        budgetSeeder.seed(users, eventsByUser);
        reviewSeeder.seed();

        log.info("Dev seed complete — {} users, {} events, {} vendor apps, {} conversations",
                users.size(), eventsByUser.stream().mapToInt(List::size).sum(), vendorApps.size(), conversations.size());
    }

    private void backfillIfMissing() {
        boolean needsGuests    = guestRepository.count() == 0;
        boolean needsProgramme = programmeItemRepository.count() == 0;
        boolean needsContracts = vendorContractRepository.count() == 0;
        boolean needsTickets   = ticketRepository.count() == 0;
        boolean needsBudgets   = eventBudgetRepository.count() == 0;
        boolean needsReviews   = vendorRatingRepository.count() == 0;
        boolean needsSemicolon = !userRepository.existsByEmail(SEMICOLON_EMAIL);

        if (!needsGuests && !needsProgramme && !needsContracts && !needsTickets
                && !needsBudgets && !needsReviews && !needsSemicolon) {
            log.info("All seed data present — nothing to backfill");
            return;
        }

        log.info("Backfilling missing seed data: guests={}, programme={}, contracts={}, tickets={}, budgets={}, reviews={}, semicolon={}",
                needsGuests, needsProgramme, needsContracts, needsTickets,
                needsBudgets, needsReviews, needsSemicolon);

        List<User> users = userSeeder.reloadOrdered();
        List<List<Events>> eventsByUser = eventSeeder.reloadGroupedByUser(users);

        if (needsSemicolon) {
            User semicolonUser = userSeeder.seedSemicolon();
            List<Events> dreamDevEvents = eventSeeder.seedDreamDev(semicolonUser);
            if (!dreamDevEvents.isEmpty()) {
                programmeSeeder.seedDreamDevProgramme(dreamDevEvents.get(0));
                budgetSeeder.seedDreamDevBudget(dreamDevEvents.get(0), semicolonUser);
            }
        }

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
        if (needsBudgets) {
            budgetSeeder.seed(users, eventsByUser);
        }
        if (needsReviews) {
            reviewSeeder.seed();
        }

        log.info("Backfill complete");
    }
}
