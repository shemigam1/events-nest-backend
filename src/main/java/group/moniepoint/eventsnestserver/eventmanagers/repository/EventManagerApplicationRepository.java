package group.moniepoint.eventsnestserver.eventmanagers.repository;

import group.moniepoint.eventsnestserver.eventmanagers.model.EventManagerApplication;
import group.moniepoint.eventsnestserver.eventmanagers.model.ManagerApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventManagerApplicationRepository extends JpaRepository<EventManagerApplication, UUID> {

    @EntityGraph(attributePaths = {"applicant", "reviewedBy"})
    Optional<EventManagerApplication> findByApplicantId(String applicantId);

    @EntityGraph(attributePaths = {"applicant", "reviewedBy"})
    Page<EventManagerApplication> findAllByStatus(ManagerApplicationStatus status, Pageable pageable);
}
