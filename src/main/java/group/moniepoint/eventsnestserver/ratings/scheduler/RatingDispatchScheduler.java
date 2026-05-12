package group.moniepoint.eventsnestserver.ratings.scheduler;

import group.moniepoint.eventsnestserver.email.EmailOutbox;
import group.moniepoint.eventsnestserver.ratings.model.RatingForm;
import group.moniepoint.eventsnestserver.ratings.service.RatingService;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RatingDispatchScheduler {

    private static final int MAX_DELAY_HOURS = 168; // 1 week — scan window

    private final RatingService ratingService;
    private final TicketRepository ticketRepository;
    private final EmailOutbox emailOutbox;

    /** Runs every hour. Finds forms whose event ended >= sendDelayHours ago and dispatches emails. */
    @Scheduled(fixedDelay = 3_600_000)
    public void dispatch() {
        List<RatingForm> candidates = ratingService.findFormsReadyToDispatch(MAX_DELAY_HOURS);

        for (RatingForm form : candidates) {
            LocalDateTime eventEnd = form.getEvent().getEndTime();
            LocalDateTime dispatchAfter = eventEnd.plusHours(form.getSendDelayHours());

            if (LocalDateTime.now().isBefore(dispatchAfter)) {
                continue; // not yet due
            }

            try {
                List<group.moniepoint.eventsnestserver.auth.model.User> attendees =
                        ticketRepository.findAttendeesByEventId(form.getEvent().getId());

                for (var attendee : attendees) {
                    emailOutbox.enqueueRatingRequest(attendee, form);
                }

                ratingService.markDispatched(form.getId());
                log.info("Rating dispatch: sent {} emails for form {} (event: {})",
                        attendees.size(), form.getId(), form.getEvent().getId());
            } catch (Exception e) {
                log.error("Rating dispatch failed for form {}: {}", form.getId(), e.getMessage(), e);
            }
        }
    }
}
