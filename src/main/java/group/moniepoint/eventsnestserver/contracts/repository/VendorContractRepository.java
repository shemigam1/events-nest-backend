package group.moniepoint.eventsnestserver.contracts.repository;

import group.moniepoint.eventsnestserver.contracts.model.VendorContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VendorContractRepository extends JpaRepository<VendorContract, UUID> {

    @Query("SELECT c FROM VendorContract c WHERE c.event.id = :eventId ORDER BY c.createdAt DESC")
    List<VendorContract> findAllByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT c FROM VendorContract c WHERE c.vendor.id = :vendorId ORDER BY c.createdAt DESC")
    List<VendorContract> findAllByVendorId(@Param("vendorId") String vendorId);

    @Query("SELECT c FROM VendorContract c WHERE c.organizer.id = :organizerId ORDER BY c.createdAt DESC")
    List<VendorContract> findAllByOrganizerId(@Param("organizerId") String organizerId);
}
