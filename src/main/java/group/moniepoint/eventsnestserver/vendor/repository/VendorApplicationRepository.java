package group.moniepoint.eventsnestserver.vendor.repository;

import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplicationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VendorApplicationRepository extends JpaRepository<VendorApplication, UUID> {

    @EntityGraph(attributePaths = {"applicant", "event"})
    List<VendorApplication> findAllByEventIdOrderByCreatedAtDesc(UUID eventId);

    @EntityGraph(attributePaths = {"event", "event.createdBy"})
    List<VendorApplication> findAllByApplicantIdOrderByCreatedAtDesc(String applicantId);

    @EntityGraph(attributePaths = {"applicant", "event"})
    List<VendorApplication> findAllByEventIdAndStatusOrderByCreatedAtDesc(
            UUID eventId, VendorApplicationStatus status);

    boolean existsByEventIdAndApplicantIdAndServiceType(UUID eventId, String applicantId, String serviceType);

    /**
     * All ACCEPTED applications for a vendor, ordered by event start time.
     * Used to build the vendor's schedule and completed-work list.
     */
    @Query("""
            SELECT a FROM VendorApplication a
            JOIN FETCH a.event e
            LEFT JOIN FETCH a.reviewedBy
            WHERE a.applicant.id = :vendorId
              AND a.status = 'ACCEPTED'
            ORDER BY e.startTime ASC
            """)
    List<VendorApplication> findAcceptedByVendorIdOrderByEventStart(@Param("vendorId") String vendorId);
}
