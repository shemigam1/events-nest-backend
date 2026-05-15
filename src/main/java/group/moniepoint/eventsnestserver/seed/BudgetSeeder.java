package group.moniepoint.eventsnestserver.seed;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.budget.model.*;
import group.moniepoint.eventsnestserver.budget.repository.BudgetLineItemRepository;
import group.moniepoint.eventsnestserver.budget.repository.EventBudgetRepository;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetSeeder {

    private final EventBudgetRepository    eventBudgetRepository;
    private final BudgetLineItemRepository budgetLineItemRepository;

    record LineItemSeed(
            BudgetCategory category, String description,
            BigDecimal planned, BigDecimal actual, LineItemStatus status) {}

    // organizer is passed explicitly to avoid lazy-loading event.getCreatedBy()
    void seed(List<User> users, List<List<Events>> eventsByUser) {
        User akin = users.get(0);
        seedAkinBudgets(eventsByUser.get(0), akin);

        if (eventsByUser.size() > 32 && users.size() > 32) {
            List<Events> semicolonEvents = eventsByUser.get(32);
            if (!semicolonEvents.isEmpty()) {
                seedDreamDevBudget(semicolonEvents.get(0), users.get(32));
            }
        }

        log.info("Budget seeding complete");
    }

    void seedDreamDevBudget(Events dreamDevEvent, User organizer) {
        seedEventBudget(dreamDevEvent, organizer,
                new BigDecimal("250000"), "Budget for DreamDev demo day.",
                List.of(
                        new LineItemSeed(BudgetCategory.VENUE,     "Venue hire and setup for the demo day",               new BigDecimal("80000"),  new BigDecimal("80000"),  LineItemStatus.PAID),
                        new LineItemSeed(BudgetCategory.CATERING,  "Refreshments and lunch for attendees and judges",      new BigDecimal("60000"),  new BigDecimal("58000"),  LineItemStatus.PAID),
                        new LineItemSeed(BudgetCategory.AV,        "Projectors, microphones and livestream equipment",     new BigDecimal("55000"),  null,                     LineItemStatus.PLANNED),
                        new LineItemSeed(BudgetCategory.MARKETING, "Event banners, programmes and social media promotion", new BigDecimal("30000"),  null,                     LineItemStatus.PLANNED),
                        new LineItemSeed(BudgetCategory.OTHER,     "Certificates, trophies and award materials",           new BigDecimal("25000"),  null,                     LineItemStatus.PLANNED)
                ));
    }

    private void seedAkinBudgets(List<Events> events, User akin) {
        if (!events.isEmpty()) {
            seedEventBudget(events.get(0), akin,
                    new BigDecimal("1500000"), "Full budget for TechFest Lagos 2026.",
                    List.of(
                            new LineItemSeed(BudgetCategory.VENUE,     "Eko Convention Centre hire and stage setup",                  new BigDecimal("500000"),  new BigDecimal("480000"),  LineItemStatus.PAID),
                            new LineItemSeed(BudgetCategory.CATERING,  "Catering for 500 guests — canapes, small chops, cocktails",   new BigDecimal("450000"),  new BigDecimal("450000"),  LineItemStatus.PAID),
                            new LineItemSeed(BudgetCategory.AV,        "PA system, LED wall, lighting rig and livestream production",  new BigDecimal("250000"),  null,                      LineItemStatus.PLANNED),
                            new LineItemSeed(BudgetCategory.MARKETING, "Digital ads, social media campaign and print materials",      new BigDecimal("150000"),  null,                      LineItemStatus.PLANNED),
                            new LineItemSeed(BudgetCategory.SECURITY,  "Crowd management — 20 trained security personnel",            new BigDecimal("150000"),  null,                      LineItemStatus.PLANNED)
                    ));
        }

        if (events.size() > 1) {
            seedEventBudget(events.get(1), akin,
                    new BigDecimal("720000"), "Budget for AI & Machine Learning Summit.",
                    List.of(
                            new LineItemSeed(BudgetCategory.VENUE,     "Landmark Centre conference hall hire",                        new BigDecimal("280000"),  null,  LineItemStatus.PLANNED),
                            new LineItemSeed(BudgetCategory.CATERING,  "Coffee, refreshments and boxed lunch for 300 attendees",      new BigDecimal("180000"),  null,  LineItemStatus.PLANNED),
                            new LineItemSeed(BudgetCategory.AV,        "Projectors, microphones and live coding display setup",        new BigDecimal("150000"),  null,  LineItemStatus.PLANNED),
                            new LineItemSeed(BudgetCategory.STAFFING,  "Event staff — registration desk and session coordinators",    new BigDecimal("80000"),   null,  LineItemStatus.PLANNED),
                            new LineItemSeed(BudgetCategory.OTHER,     "Speaker honoraria and travel reimbursements",                 new BigDecimal("30000"),   null,  LineItemStatus.PLANNED)
                    ));
        }

        if (events.size() > 2) {
            seedEventBudget(events.get(2), akin,
                    new BigDecimal("420000"), "Budget for Blockchain Developers Bootcamp.",
                    List.of(
                            new LineItemSeed(BudgetCategory.VENUE,     "Training room hire with breakout areas",                      new BigDecimal("180000"),  null,  LineItemStatus.PLANNED),
                            new LineItemSeed(BudgetCategory.CATERING,  "Working lunch and refreshments for 50 participants",          new BigDecimal("120000"),  null,  LineItemStatus.PLANNED),
                            new LineItemSeed(BudgetCategory.AV,        "Development workstations, projectors and demo hardware",      new BigDecimal("80000"),   null,  LineItemStatus.PLANNED),
                            new LineItemSeed(BudgetCategory.OTHER,     "Bootcamp materials, handbooks and USB devices",               new BigDecimal("40000"),   null,  LineItemStatus.PLANNED)
                    ));
        }

        if (events.size() > 4) {
            Events pastEvent = events.get(4);
            boolean isPast = pastEvent.getStatus() == EventStatus.PUBLISHED
                    && pastEvent.getStartTime() != null
                    && pastEvent.getStartTime().isBefore(LocalDateTime.now());

            seedEventBudget(pastEvent, akin,
                    new BigDecimal("980000"), "Final actuals for Digital Innovation Conference.",
                    List.of(
                            new LineItemSeed(BudgetCategory.VENUE,     "Federal Palace Hotel conference hall",                        new BigDecimal("400000"),  new BigDecimal("400000"),  isPast ? LineItemStatus.PAID : LineItemStatus.PLANNED),
                            new LineItemSeed(BudgetCategory.CATERING,  "Full-day catering for 400 delegates",                         new BigDecimal("220000"),  new BigDecimal("215000"),  isPast ? LineItemStatus.PAID : LineItemStatus.PLANNED),
                            new LineItemSeed(BudgetCategory.AV,        "Full AV production — PA, LED walls and recording",             new BigDecimal("180000"),  new BigDecimal("180000"),  isPast ? LineItemStatus.PAID : LineItemStatus.PLANNED),
                            new LineItemSeed(BudgetCategory.MARKETING, "Pre-event digital campaign and on-site branding",             new BigDecimal("100000"),  new BigDecimal("95000"),   isPast ? LineItemStatus.PAID : LineItemStatus.PLANNED),
                            new LineItemSeed(BudgetCategory.SECURITY,  "Security team for the day",                                   new BigDecimal("80000"),   new BigDecimal("80000"),   isPast ? LineItemStatus.PAID : LineItemStatus.PLANNED)
                    ));
        }
    }

    void seedEventBudget(Events event, User organizer, BigDecimal total, String notes, List<LineItemSeed> lineItems) {
        if (eventBudgetRepository.existsByEventId(event.getId())) return;

        EventBudget budget = eventBudgetRepository.save(EventBudget.builder()
                .event(event)
                .totalBudget(total)
                .notes(notes)
                .createdBy(organizer)
                .build());

        for (LineItemSeed li : lineItems) {
            budgetLineItemRepository.save(BudgetLineItem.builder()
                    .budget(budget)
                    .category(li.category())
                    .description(li.description())
                    .plannedAmount(li.planned())
                    .actualAmount(li.actual())
                    .status(li.status())
                    .paidAt(li.status() == LineItemStatus.PAID ? LocalDateTime.now().minusDays(5) : null)
                    .createdBy(organizer)
                    .build());
        }

        log.debug("Seeded budget for: {} (₦{})", event.getTitle(), total);
    }
}
