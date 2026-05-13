package group.moniepoint.eventsnestserver.auth.repository;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.model.VendorVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByVendorVerificationStatusOrderByVendorVerificationSubmittedAtAsc(
            VendorVerificationStatus status);

    /** All platform-verified vendors, no service-type filter. */
    @Query("""
            SELECT u FROM User u
            WHERE u.vendorVerified = true
            ORDER BY u.vendorVerifiedAt DESC
            """)
    List<User> findVerifiedVendors();

    /** All platform-verified vendors whose service type contains the given string (case-insensitive). */
    @Query("""
            SELECT u FROM User u
            WHERE u.vendorVerified = true
              AND LOWER(u.vendorServiceType) LIKE LOWER(CONCAT('%', :serviceType, '%'))
            ORDER BY u.vendorVerifiedAt DESC
            """)
    List<User> findVerifiedVendorsByServiceType(@Param("serviceType") String serviceType);
}
