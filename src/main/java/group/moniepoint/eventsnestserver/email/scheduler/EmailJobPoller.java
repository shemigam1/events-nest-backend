package group.moniepoint.eventsnestserver.email.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import group.moniepoint.eventsnestserver.common.logging.CorrelationIdFilter;
import group.moniepoint.eventsnestserver.email.EmailService;
import group.moniepoint.eventsnestserver.email.model.EmailJob;
import group.moniepoint.eventsnestserver.email.model.EmailJobStatus;
import group.moniepoint.eventsnestserver.email.model.EmailJobType;
import group.moniepoint.eventsnestserver.email.payload.BookingConfirmationPayload;
import group.moniepoint.eventsnestserver.email.payload.EventApprovedPayload;
import group.moniepoint.eventsnestserver.email.payload.EventRejectedPayload;
import group.moniepoint.eventsnestserver.email.payload.GuestRsvpInvitePayload;
import group.moniepoint.eventsnestserver.email.payload.RatingRequestPayload;
import group.moniepoint.eventsnestserver.email.payload.StaffInvitePayload;
import group.moniepoint.eventsnestserver.email.repository.EmailJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailJobPoller {

    private final EmailJobRepository emailJobRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${email.job.poll-interval-ms:30000}")
    public void processPendingJobs() {
        // Each poll batch gets a fresh correlation ID so its log lines (and
        // any errors that bubble out of EmailService) cluster together when
        // grepping. Scheduled tasks have no inbound HTTP request to inherit
        // an ID from.
        String batchId = "email-poll-" + UUID.randomUUID().toString().substring(0, 8);
        MDC.put(CorrelationIdFilter.MDC_KEY, batchId);
        try {
            doProcessPendingJobs();
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private void doProcessPendingJobs() {
        List<EmailJob> jobs = emailJobRepository.findPendingJobs(EmailJobStatus.PENDING);

        if (jobs.isEmpty()) return;

        log.debug("Processing {} pending email job(s)", jobs.size());

        for (EmailJob job : jobs) {
            try {
                dispatch(job);

                job.setStatus(EmailJobStatus.SENT);
                // Sensitive content (staff token, payload JSON) is purged on
                // successful delivery so it doesn't sit in the DB indefinitely.
                job.setRawToken(null);
                job.setPayloadJson(null);
                log.info("Email job {} ({}) sent to {}", job.getId(), effectiveType(job), job.getToEmail());

                // Phase B telemetry — tagged by jobType so the dashboard can
                // break out send rates per email category if useful.
                meterRegistry.counter("eventsnest.email.jobs.sent",
                        "jobType", effectiveType(job).name()).increment();

            } catch (Exception e) {
                log.error("Email job {} failed (attempt {}/{}): {}",
                        job.getId(), job.getAttempts() + 1, job.getMaxAttempts(), e.getMessage());
                job.setFailureReason(e.getMessage());

                if (job.getAttempts() + 1 >= job.getMaxAttempts()) {
                    job.setStatus(EmailJobStatus.FAILED);
                    log.warn("Email job {} permanently failed after {} attempts", job.getId(), job.getMaxAttempts());
                    meterRegistry.counter("eventsnest.email.jobs.failed",
                            "jobType", effectiveType(job).name()).increment();
                }
            }

            job.setAttempts(job.getAttempts() + 1);
            job.setLastAttemptAt(LocalDateTime.now());
            emailJobRepository.save(job);
        }
    }

    /**
     * Hand the row off to the right EmailService method based on its type.
     * Rows persisted before the {@code job_type} column existed read back
     * as {@code null} — those are treated as STAFF_INVITE for backward
     * compatibility.
     */
    private void dispatch(EmailJob job) throws Exception {
        switch (effectiveType(job)) {
            case STAFF_INVITE -> {
                // New rows write a StaffInvitePayload into payload_json; legacy
                // rows (persisted before this change) only have the dedicated
                // staff_* columns. Fall back to the legacy fields when payload
                // is absent so old PENDING rows still drain on first deploy.
                if (job.getPayloadJson() != null) {
                    StaffInvitePayload p = objectMapper.readValue(
                            job.getPayloadJson(), StaffInvitePayload.class);
                    emailService.sendCheckInStaffInvite(
                            job.getToEmail(),
                            p.name(),
                            p.rawToken(),
                            p.eventTitle(),
                            p.eventId(),
                            p.eventCode());
                } else {
                    emailService.sendCheckInStaffInvite(
                            job.getToEmail(),
                            job.getStaffName(),
                            job.getRawToken(),
                            job.getEventTitle(),
                            null,
                            null);
                }
            }

            case BOOKING_CONFIRMED -> {
                BookingConfirmationPayload p = objectMapper.readValue(
                        job.getPayloadJson(), BookingConfirmationPayload.class);
                emailService.sendBookingConfirmation(
                        job.getToEmail(),
                        p.attendeeName(),
                        p.eventTitle(),
                        p.tierName(),
                        p.quantity(),
                        p.totalAmount(),
                        p.paymentReference());
            }

            case EVENT_APPROVED -> {
                EventApprovedPayload p = objectMapper.readValue(
                        job.getPayloadJson(), EventApprovedPayload.class);
                emailService.sendEventApproved(
                        job.getToEmail(),
                        p.organiserName(),
                        p.eventTitle());
            }

            case EVENT_REJECTED -> {
                EventRejectedPayload p = objectMapper.readValue(
                        job.getPayloadJson(), EventRejectedPayload.class);
                emailService.sendEventRejected(
                        job.getToEmail(),
                        p.organiserName(),
                        p.eventTitle(),
                        p.reason());
            }

            case GUEST_RSVP_INVITE -> {
                GuestRsvpInvitePayload p = objectMapper.readValue(
                        job.getPayloadJson(), GuestRsvpInvitePayload.class);
                emailService.sendGuestRsvpInvite(
                        job.getToEmail(),
                        p.guestName(),
                        p.eventTitle(),
                        p.eventId(),
                        p.rsvpToken());
            }

            case RATING_REQUEST -> {
                RatingRequestPayload p = objectMapper.readValue(
                        job.getPayloadJson(), RatingRequestPayload.class);
                emailService.sendRatingRequest(
                        job.getToEmail(),
                        p.attendeeName(),
                        p.eventTitle(),
                        p.formId());
            }
        }
    }

    private static EmailJobType effectiveType(EmailJob job) {
        return job.getType() != null ? job.getType() : EmailJobType.STAFF_INVITE;
    }
}
