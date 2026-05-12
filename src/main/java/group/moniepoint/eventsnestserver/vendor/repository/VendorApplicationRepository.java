package group.moniepoint.eventsnestserver.vendor.repository;

import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VendorApplicationRepository extends JpaRepository<VendorApplication, UUID> {

    List<VendorApplication> findAllByEventIdOrderByCreatedAtDesc(UUID eventId);

    List<VendorApplication> findAllByApplicantIdOrderByCreatedAtDesc(String applicantId);

    List<VendorApplication> findAllByEventIdAndStatusOrderByCreatedAtDesc(
            UUID eventId, VendorApplicationStatus status);

    boolean existsByEventIdAndApplicantIdAndServiceType(UUID eventId, String applicantId, String serviceType);
}
