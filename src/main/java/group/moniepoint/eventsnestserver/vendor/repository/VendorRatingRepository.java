package group.moniepoint.eventsnestserver.vendor.repository;

import group.moniepoint.eventsnestserver.vendor.model.VendorRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VendorRatingRepository extends JpaRepository<VendorRating, UUID> {

    Optional<VendorRating> findByVendorApplicationId(UUID applicationId);

    /** Average score across all rated applications for a given vendor (applicant). */
    @Query("""
            SELECT AVG(r.score)
            FROM VendorRating r
            WHERE r.vendorApplication.applicant.id = :vendorId
            """)
    Double findAverageScoreByVendorId(@Param("vendorId") String vendorId);

    /** Count of ratings received by a vendor. */
    @Query("""
            SELECT COUNT(r)
            FROM VendorRating r
            WHERE r.vendorApplication.applicant.id = :vendorId
            """)
    long countByVendorId(@Param("vendorId") String vendorId);
}
