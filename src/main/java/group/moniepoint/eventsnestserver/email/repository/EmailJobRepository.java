package group.moniepoint.eventsnestserver.email.repository;

import group.moniepoint.eventsnestserver.email.model.EmailJob;
import group.moniepoint.eventsnestserver.email.model.EmailJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EmailJobRepository extends JpaRepository<EmailJob, UUID> {

    @Query("SELECT j FROM EmailJob j WHERE j.status = :status AND j.attempts < j.maxAttempts ORDER BY j.createdAt ASC")
    List<EmailJob> findPendingJobs(@Param("status") EmailJobStatus status);
}
