package group.moniepoint.eventsnestserver.vendor.repository;

import group.moniepoint.eventsnestserver.vendor.model.VendorInquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorInquiryRepository extends JpaRepository<VendorInquiry, UUID> {

    @Query("SELECT i FROM VendorInquiry i WHERE i.event.id = :eventId AND i.organizer.id = :organizerId ORDER BY i.createdAt DESC")
    List<VendorInquiry> findByEventIdAndOrganizerId(@Param("eventId") UUID eventId,
                                                     @Param("organizerId") String organizerId);

    @Query("SELECT i FROM VendorInquiry i WHERE i.vendor.id = :vendorId ORDER BY i.createdAt DESC")
    List<VendorInquiry> findByVendorId(@Param("vendorId") String vendorId);

    boolean existsByEventIdAndOrganizerIdAndVendorId(UUID eventId, String organizerId, String vendorId);

    Optional<VendorInquiry> findByEventIdAndOrganizerIdAndVendorId(UUID eventId, String organizerId, String vendorId);
}
