package group.moniepoint.eventsnestserver.email.scheduler;

import group.moniepoint.eventsnestserver.email.EmailService;
import group.moniepoint.eventsnestserver.email.model.EmailJob;
import group.moniepoint.eventsnestserver.email.model.EmailJobStatus;
import group.moniepoint.eventsnestserver.email.repository.EmailJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailJobPoller {

    private final EmailJobRepository emailJobRepository;
    private final EmailService emailService;

    @Scheduled(fixedDelayString = "${email.job.poll-interval-ms:30000}")
    @Transactional
    public void processPendingJobs() {
        List<EmailJob> jobs = emailJobRepository.findPendingJobs(EmailJobStatus.PENDING);

        if (jobs.isEmpty()) return;

        log.debug("Processing {} pending email job(s)", jobs.size());

        for (EmailJob job : jobs) {
            try {
                emailService.sendCheckInStaffInvite(
                        job.getToEmail(),
                        job.getStaffName(),
                        job.getRawToken(),
                        job.getEventTitle());

                job.setStatus(EmailJobStatus.SENT);
                job.setRawToken(null); // clear token from DB after successful delivery
                log.info("Email job {} sent successfully to {}", job.getId(), job.getToEmail());

            } catch (Exception e) {
                log.error("Email job {} failed (attempt {}/{}): {}",
                        job.getId(), job.getAttempts() + 1, job.getMaxAttempts(), e.getMessage());
                job.setFailureReason(e.getMessage());

                if (job.getAttempts() + 1 >= job.getMaxAttempts()) {
                    job.setStatus(EmailJobStatus.FAILED);
                    log.warn("Email job {} permanently failed after {} attempts", job.getId(), job.getMaxAttempts());
                }
            }

            job.setAttempts(job.getAttempts() + 1);
            job.setLastAttemptAt(LocalDateTime.now());
            emailJobRepository.save(job);
        }
    }
}
